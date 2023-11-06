package org.fiume.sketch.storage.documents.http

import cats.data.OptionT
import cats.effect.{IO, Ref}
import cats.implicits.*
import io.circe.syntax.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import munit.Assertions.*
import org.fiume.sketch.shared.app.WithUuid
import org.fiume.sketch.shared.app.http4s.middlewares.{SemanticInputError, SemanticValidationMiddleware}
import org.fiume.sketch.shared.app.troubleshooting.{ErrorInfo, ErrorMessage}
import org.fiume.sketch.shared.app.troubleshooting.http.json.ErrorInfoCodecs.given
import org.fiume.sketch.shared.auth0.User
import org.fiume.sketch.shared.auth0.testkit.UserGens.given
import org.fiume.sketch.shared.auth0.testkit.UserGens.userIds
import org.fiume.sketch.shared.testkit.{ContractContext, Http4sTestingRoutesDsl}
import org.fiume.sketch.shared.testkit.EitherSyntax.*
import org.fiume.sketch.storage.documents.{Document, DocumentId, DocumentWithStream, DocumentWithUuidAndStream}
import org.fiume.sketch.storage.documents.algebras.DocumentsStore
import org.fiume.sketch.storage.documents.http.DocumentsRoutes.Model.*
import org.fiume.sketch.storage.documents.http.DocumentsRoutes.Model.Json.given
import org.fiume.sketch.storage.testkit.DocumentsGens.*
import org.fiume.sketch.storage.testkit.DocumentsGens.given
import org.http4s.{MediaType, *}
import org.http4s.Method.*
import org.http4s.client.dsl.io.*
import org.http4s.headers.`Content-Type`
import org.http4s.implicits.*
import org.http4s.multipart.{Boundary, Multipart, Part}
import org.http4s.server.AuthMiddleware
import org.scalacheck.{Arbitrary, Gen, ShrinkLowPriority}
import org.scalacheck.effect.PropF.forAllF

class DocumentsRoutesSpec
    extends CatsEffectSuite
    with ScalaCheckEffectSuite
    with Http4sTestingRoutesDsl
    with ContractContext
    with DocumentsStoreContext
    with DocumentsRoutesSpecContext
    with ShrinkLowPriority:

  override def scalaCheckTestParameters = super.scalaCheckTestParameters.withMinSuccessfulTests(10)

  test("Post document"):
    forAllF { (metadataPayload: MetadataRequestPayload, user: User) =>
      val multipart = Multipart[IO](
        parts = Vector(
          Part.formData("metadata", metadataPayload.asJson.spaces2SortKeys),
          Part.fileData("bytes", montainBikeInLiguriaImageFile, `Content-Type`(MediaType.image.jpeg))
        ),
        boundary = Boundary("boundary")
      )
      val request = POST(uri"/documents").withEntity(multipart).withHeaders(multipart.headers)
      for
        store <- makeDocumentsStore()
        authMiddleware = makeAuthMiddleware(authenticated = user)
        documentsRoutes <- makeDocumentsRoutes(authMiddleware, store)

        jsonResponse <- send(request)
          .to(documentsRoutes.router())
          .expectJsonResponseWith(Status.Created)

        stored <- store.fetchDocument(jsonResponse.as[DocumentId].rightValue)
        uploadedContent <- bytesFrom[IO]("mountain-bike-liguria-ponent.jpg").compile.toList
        storedBytes <- OptionT(
          store.documentStream(jsonResponse.as[DocumentId].rightValue)
        ).semiflatMap(_.compile.toList).value
        _ <- IO {
          assertEquals(
            stored.map(_.metadata.toResponsePayload),
            MetadataResponsePayload(
              name = metadataPayload.name,
              description = metadataPayload.description,
              createdBy = user.uuid.value.toString,
              ownedBy = metadataPayload.ownedBy
            ).some
          )
          assertEquals(storedBytes.map(_.toList), uploadedContent.some)
        }
      yield ()
    }

  test("Get document metadata"):
    forAllF { (document: DocumentWithUuidAndStream[IO]) =>
      val request = GET(Uri.unsafeFromString(s"/documents/${document.uuid.value}/metadata"))
      for
        store <- makeDocumentsStore(state = document)
        authMiddleware = makeAuthMiddleware()
        documentsRoutes <- makeDocumentsRoutes(authMiddleware, store)

        jsonResponse <- send(request)
          .to(documentsRoutes.router())
          .expectJsonResponseWith(Status.Ok)

        _ <- IO {
          assertEquals(
            jsonResponse.as[MetadataResponsePayload].rightValue,
            document.metadata.toResponsePayload
          )
        }
      yield ()
    }

  test("Get document content"):
    forAllF { (document: DocumentWithUuidAndStream[IO]) =>
      val request = GET(Uri.unsafeFromString(s"/documents/${document.uuid.value}"))
      for
        store <- makeDocumentsStore(state = document)
        authMiddleware = makeAuthMiddleware()
        documentsRoutes <- makeDocumentsRoutes(authMiddleware, store)

        contentStream <- send(request)
          .to(documentsRoutes.router())
          .expectByteStreamResponseWith(Status.Ok)

        obtainedStream <- contentStream.compile.toList
        expectedStream <- document.stream.compile.toList
        _ <- IO {
          assertEquals(obtainedStream, expectedStream)
        }
      yield ()
    }

  test("Delete document"):
    forAllF { (document: DocumentWithUuidAndStream[IO]) =>
      val request = DELETE(Uri.unsafeFromString(s"/documents/${document.uuid.value}"))
      for
        store <- makeDocumentsStore(state = document)
        authMiddleware = makeAuthMiddleware()
        documentsRoutes <- makeDocumentsRoutes(authMiddleware, store)

        _ <- send(request)
          .to(documentsRoutes.router())
          .expectEmptyResponseWith(Status.NoContent)

        stored <- IO.both(
          store.fetchDocument(document.uuid),
          OptionT(store.documentStream(document.uuid)).semiflatMap(_.compile.toList).value
        )
        _ <- IO {
          assertEquals(stored._1, none)
          assertEquals(stored._2, none)
        }
      yield ()
    }

  test("Delete unexistent document == not found"):
    forAllF { (document: DocumentWithUuidAndStream[IO]) =>
      val request = DELETE(Uri.unsafeFromString(s"/documents/${document.uuid.value}"))
      for
        store <- makeDocumentsStore()
        authMiddleware = makeAuthMiddleware()
        documentsRoutes <- makeDocumentsRoutes(authMiddleware, store)
        _ <- send(request)
          .to(documentsRoutes.router())
          .expectEmptyResponseWith(Status.NotFound)
      yield ()
    }

  /* Sad Path */

  test("return 422 when document upload request is semantically invalid"):
    forAllF(semanticallyInvalidDocumentRequests) { (multipart: Multipart[IO]) =>
      for
        store <- makeDocumentsStore()
        authMiddleware = makeAuthMiddleware()
        documentsRoutes <- makeDocumentsRoutes(authMiddleware, store)

        request = POST(uri"/documents").withEntity(multipart).withHeaders(multipart.headers)
        result <- send(request)
          .to(SemanticValidationMiddleware(documentsRoutes.router()))
          .expectJsonResponseWith(Status.UnprocessableEntity)
          .map(_.as[ErrorInfo].rightValue)

        _ <- IO {
          assertEquals(result.message, SemanticInputError.message)
          assert(
            result.details.get.tips.keySet.subsetOf(
              Set("missing.document.metadata.part", "missing.document.bytes.part", "document.name.too.short")
            ),
            clue = result.details.get.tips.mkString
          )
        }
      yield ()
    }

  test("return 422 when document upload request is malformed"):
    forAllF(malformedDocumentRequests) { (multipart: Multipart[IO]) =>
      for
        store <- makeDocumentsStore()
        authMiddleware = makeAuthMiddleware()
        documentsRoutes <- makeDocumentsRoutes(authMiddleware, store)

        request = POST(uri"/documents").withEntity(multipart).withHeaders(multipart.headers)
        result <- send(request)
          .to(SemanticValidationMiddleware(documentsRoutes.router()))
          .expectJsonResponseWith(Status.UnprocessableEntity)
          .map(_.as[ErrorInfo].rightValue)

        _ <- IO {
          assertEquals(result.message, SemanticInputError.message)
          assertEquals(result.details.get.tips,
                       Map("malformed.document.metadata.payload" -> "the metadata payload does not meet the contract")
          )
        }
      yield ()
    }

  /*
   * Contracts
   */

  test("bijective relationship between encoded and decoded Documents.Metadata"):
    assertBijectiveRelationshipBetweenEncoderAndDecoder[MetadataRequestPayload](
      "contract/documents/http/document.json" // TODO Change it
    )

  test("validation accumulates") {
    /* Also see `given accumulatingParallel: cats.Parallel[EitherT[IO, String, *]] = EitherT.accumulatingParallel` */
    // no metadata part / no bytes part
    val noMultiparts = Multipart[IO](parts = Vector.empty, boundary = Boundary("boundary"))
    val createdBy = userIds.sample.get
    for
      inputErrors <- noMultiparts.validated(createdBy).attempt.map(_.leftValue)

      _ <- IO {
        assert(
          inputErrors.asInstanceOf[SemanticInputError].details.tips.size > 1,
          clue = "errors must accumulate"
        )
      }
    yield ()

  }

trait DocumentsRoutesSpecContext extends AuthMiddlewareContext:

  def montainBikeInLiguriaImageFile = getClass.getClassLoader.getResource("mountain-bike-liguria-ponent.jpg")

  given Arbitrary[MetadataRequestPayload] = Arbitrary(metadataRequestPayloads)
  def metadataRequestPayloads: Gen[MetadataRequestPayload] = metadataG.map(_.toRequestPayload) :| "metadataRequestPayloads"

  def malformedDocumentRequests: Gen[Multipart[IO]] = Gen.delay {
    Multipart[IO](
      parts = Vector(
        Part.formData("metadata", """ { \"bananas\" : \"apples\" } """),
        Part.fileData("bytes", montainBikeInLiguriaImageFile, `Content-Type`(MediaType.image.jpeg))
      ),
      boundary = Boundary("boundary")
    )
  } :| "invalidDocumentRequestWithMalformedMetadataAndNoBytes"

  def semanticallyInvalidDocumentRequests: Gen[Multipart[IO]] = Gen.oneOf(
    invalidPartWithNoContent,
    invalidPartWithNoMetadata,
    invalidTooShortDocumentName
  )

  private def invalidPartWithNoContent: Gen[Multipart[IO]] = metadataRequestPayloads.flatMap { metadata =>
    Gen.delay {
      Multipart[IO](
        // no file mamma!
        parts = Vector(Part.formData("metadata", metadata.asJson.spaces2SortKeys)),
        boundary = Boundary("boundary")
      )
    }
  } :| "invalidPartWithNoContent"

  private def invalidPartWithNoMetadata: Gen[Multipart[IO]] = Gen.delay {
    Multipart[IO](
      // no metadata mamma!
      parts = Vector(
        Part.fileData("bytes", montainBikeInLiguriaImageFile, `Content-Type`(MediaType.image.jpeg))
      ),
      boundary = Boundary("boundary")
    )
  } :| "invalidPartWithNoMetadata"

  def invalidTooShortDocumentName: Gen[Multipart[IO]] =
    (for
      name <- shortNames
      metadata <- metadataRequestPayloads.map(_.copy(name = name))
    yield Multipart[IO](
      parts = Vector(
        Part.formData("metadata", metadata.asJson.spaces2SortKeys),
        Part.fileData("bytes", montainBikeInLiguriaImageFile, `Content-Type`(MediaType.image.jpeg))
      ),
      boundary = Boundary("boundary")
    )) :| "invalidTooShortDocumentName"

  def makeDocumentsRoutes(authMiddleware: AuthMiddleware[IO, User],
                          withStore: DocumentsStore[IO, IO]
  ): IO[DocumentsRoutes[IO, IO]] =
    val documentBytesSizeLimit = 5 * 1024 * 1024
    IO.delay { new DocumentsRoutes[IO, IO](authMiddleware, documentBytesSizeLimit, withStore) }

trait AuthMiddlewareContext:
  import cats.data.Kleisli
  import org.fiume.sketch.shared.auth0.User
  import org.http4s.server.AuthMiddleware
  import org.http4s.Request
  import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo
  import org.fiume.sketch.shared.app.troubleshooting.ErrorDetails
  import org.http4s.circe.CirceEntityEncoder.*
  import org.fiume.sketch.shared.app.troubleshooting.http.json.ErrorInfoCodecs.given
  import org.fiume.sketch.shared.auth0.testkit.UserGens.*
  import org.http4s.headers.`WWW-Authenticate`
  import org.http4s.Challenge
  import org.http4s.Status
  import org.http4s.Response

  def makeAuthMiddleware(): AuthMiddleware[IO, User] =
    def aUser(): User = users.sample.get
    makeAuthMiddleware(aUser())

  def makeAuthMiddleware(authenticated: User): AuthMiddleware[IO, User] =
    def verify: Kleisli[IO, Request[IO], Either[String, User]] = Kleisli.liftF(authenticated.asRight[String].pure[IO])

    val onFailure: AuthedRoutes[String, IO] = Kleisli { cx =>
      OptionT.pure(
        Response[IO](Status.Unauthorized)
          .withHeaders(`WWW-Authenticate`(Challenge("Bearer", s"${cx.req.uri.path}")))
          .withEntity(
            ErrorInfo.withDetails(ErrorMessage("Invalid credentials"), ErrorDetails.single("invalid.jwt" -> cx.context))
          )
      )
    }
    AuthMiddleware(verify, onFailure)

trait DocumentsStoreContext:
  import fs2.Stream
  import org.fiume.sketch.storage.documents.{Document, DocumentId, DocumentWithId, WithStream}

  def makeDocumentsStore(): IO[DocumentsStore[IO, IO]] = makeDocumentsStore(state = Map.empty)

  def makeDocumentsStore(state: DocumentWithUuidAndStream[IO]): IO[DocumentsStore[IO, IO]] =
    makeDocumentsStore(Map(state.uuid -> state))

  private def makeDocumentsStore(state: Map[DocumentId, DocumentWithUuidAndStream[IO]]): IO[DocumentsStore[IO, IO]] =
    Ref.of[IO, Map[DocumentId, DocumentWithUuidAndStream[IO]]](state).map { storage =>
      new DocumentsStore[IO, IO]:
        def store(document: DocumentWithStream[IO]): IO[DocumentId] =
          import scala.language.adhocExtensions
          IO.randomUUID.map(DocumentId(_)).flatMap { uuid =>
            storage
              .update {
                val doc = new Document(document.metadata) with WithUuid[DocumentId] with WithStream[IO]:
                  val uuid = uuid
                  val stream = document.stream
                _.updated(uuid, doc)
              }
              .as(uuid)
          }

        def fetchDocument(uuid: DocumentId): IO[Option[Document]] =
          storage.get.map(_.collectFirst {
            case (storedUuid, document) if storedUuid === uuid => document
          })

        def documentStream(uuid: DocumentId): IO[Option[fs2.Stream[IO, Byte]]] =
          storage.get.map(_.collectFirst {
            case (storedUuid, document) if storedUuid === uuid => document.stream
          })

        def fetchAll(): Stream[IO, DocumentWithId] = ??? // TODO

        def delete(uuid: DocumentId): IO[Unit] =
          storage.update { _.removed(uuid) }

        val commit: [A] => IO[A] => IO[A] = [A] => (action: IO[A]) => action

        val lift: [A] => IO[A] => IO[A] = [A] => (action: IO[A]) => action
    }
