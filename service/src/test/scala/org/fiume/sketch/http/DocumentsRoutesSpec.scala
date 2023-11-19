package org.fiume.sketch.http

import cats.data.OptionT
import cats.effect.{IO, Ref}
import cats.implicits.*
import io.circe.{Decoder, Encoder, HCursor, Json}
import io.circe.Decoder.Result
import io.circe.syntax.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import munit.Assertions.*
import org.fiume.sketch.http.DocumentsRoutes.Model.*
import org.fiume.sketch.http.DocumentsRoutes.Model.json.given
import org.fiume.sketch.shared.app.WithUuid
import org.fiume.sketch.shared.app.http4s.middlewares.{SemanticInputError, SemanticValidationMiddleware}
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo.json.given
import org.fiume.sketch.shared.auth0.User
import org.fiume.sketch.shared.auth0.testkit.UserGens.given
import org.fiume.sketch.shared.auth0.testkit.UserGens.userIds
import org.fiume.sketch.shared.domain.documents.{Document, DocumentId, DocumentWithIdAndStream, DocumentWithStream}
import org.fiume.sketch.shared.domain.documents.algebras.DocumentsStore
import org.fiume.sketch.shared.domain.testkit.DocumentsGens.*
import org.fiume.sketch.shared.domain.testkit.DocumentsGens.given
import org.fiume.sketch.shared.testkit.{ContractContext, Http4sTestingRoutesDsl}
import org.fiume.sketch.shared.testkit.EitherSyntax.*
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

        result <- send(request)
          .to(documentsRoutes.router())
          .expectJsonResponseWith(Status.Created)

        createdDocId = result.as[DocumentIdResponsePayload].rightValue
        stored <- store.fetchDocument(createdDocId.value)
        _ <- IO {
          assert(stored.isDefined, clue = stored)
        }
      yield ()
    }

  test("Get document"):
    forAllF { (document: DocumentWithIdAndStream[IO]) =>
      val request = GET(Uri.unsafeFromString(s"/documents/${document.uuid.value}/metadata"))
      for
        store <- makeDocumentsStore(state = document)
        authMiddleware = makeAuthMiddleware()
        documentsRoutes <- makeDocumentsRoutes(authMiddleware, store)

        result <- send(request)
          .to(documentsRoutes.router())
          .expectJsonResponseWith(Status.Ok)

        _ <- IO {
          assertEquals(result.as[DocumentResponsePayload].rightValue, document.asResponsePayload)
        }
      yield ()
    }

  test("Get document content"):
    forAllF { (document: DocumentWithIdAndStream[IO]) =>
      val request = GET(Uri.unsafeFromString(s"/documents/${document.uuid.value}"))
      for
        store <- makeDocumentsStore(state = document)
        authMiddleware = makeAuthMiddleware()
        documentsRoutes <- makeDocumentsRoutes(authMiddleware, store)

        result <- send(request)
          .to(documentsRoutes.router())
          .expectByteStreamResponseWith(Status.Ok)

        obtainedStream <- result.compile.toList
        expectedStream <- document.stream.compile.toList
        _ <- IO {
          assertEquals(obtainedStream, expectedStream)
        }
      yield ()
    }

  test("Get document by author"):
    forAllF { (fstDoc: DocumentWithIdAndStream[IO], sndDoc: DocumentWithIdAndStream[IO]) =>
      val request = GET(Uri.unsafeFromString(s"/documents?author=${sndDoc.metadata.author.asString()}"))
      for
        store <- makeDocumentsStore(state = fstDoc, sndDoc)
        authMiddleware = makeAuthMiddleware()
        documentsRoutes <- makeDocumentsRoutes(authMiddleware, store)

        result <- send(request)
          .to(documentsRoutes.router())
          .expectJsonResponseWith(Status.Ok)

        _ <- IO {
          assertEquals(
            result.as[DocumentResponsePayload].rightValue,
            sndDoc.asResponsePayload
          )
        }
      yield ()
    }

  test("Get document by owner"):
    forAllF { (fstDoc: DocumentWithIdAndStream[IO], sndDoc: DocumentWithIdAndStream[IO]) =>
      val request = GET(Uri.unsafeFromString(s"/documents?owner=${sndDoc.metadata.owner.asString()}"))
      for
        store <- makeDocumentsStore(state = fstDoc, sndDoc)
        authMiddleware = makeAuthMiddleware()
        documentsRoutes <- makeDocumentsRoutes(authMiddleware, store)

        result <- send(request)
          .to(documentsRoutes.router())
          .expectJsonResponseWith(Status.Ok)

        _ <- IO {
          assertEquals(
            result.as[DocumentResponsePayload].rightValue,
            sndDoc.asResponsePayload
          )
        }
      yield ()
    }
  test("Delete document"):
    forAllF { (document: DocumentWithIdAndStream[IO]) =>
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
    forAllF { (document: DocumentWithIdAndStream[IO]) =>
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

  test("bijective relationship between encoded and decoded document MetadataRequestPayload"):
    assertBijectiveRelationshipBetweenEncoderAndDecoder[MetadataRequestPayload]("document/metadata.request.json")

  test("bijective relationship between encoded and decoded DocumentResponsePayload"):
    assertBijectiveRelationshipBetweenEncoderAndDecoder[DocumentResponsePayload]("document/response.json")

  test("bijective relationship between encoded and decoded DocumentIdResponsePayload"):
    assertBijectiveRelationshipBetweenEncoderAndDecoder[DocumentIdResponsePayload]("document/uuid.response.json")

  test("validation accumulates") {
    /* Also see `given accumulatingParallel: cats.Parallel[EitherT[IO, String, *]] = EitherT.accumulatingParallel` */
    // no metadata part / no bytes part
    val noMultiparts = Multipart[IO](parts = Vector.empty, boundary = Boundary("boundary"))
    val author = userIds.sample.get
    for
      inputErrors <- noMultiparts.validated(author).attempt.map(_.leftValue)

      _ <- IO {
        assert(
          inputErrors.asInstanceOf[SemanticInputError].details.tips.size === 2,
          clue = inputErrors
        )
      }
    yield ()

  }

trait DocumentsRoutesSpecContext extends AuthMiddlewareContext:

  def montainBikeInLiguriaImageFile = getClass.getClassLoader.getResource("mountain-bike-liguria-ponent.jpg")

  given Arbitrary[MetadataRequestPayload] = Arbitrary(metadataRequestPayloads)
  def metadataRequestPayloads: Gen[MetadataRequestPayload] = metadataG.map(_.asRequestPayload) :| "metadataRequestPayloads"

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

  def makeDocumentsRoutes(
    authMiddleware: AuthMiddleware[IO, User],
    withStore: DocumentsStore[IO, IO]
  ): IO[DocumentsRoutes[IO, IO]] =
    val documentBytesSizeLimit = 5 * 1024 * 1024
    IO.delay { new DocumentsRoutes[IO, IO](authMiddleware, documentBytesSizeLimit, withStore) }

  extension (m: Document.Metadata)
    def asRequestPayload: MetadataRequestPayload =
      MetadataRequestPayload(m.name.value, m.description.value, m.owner.asString())

  given Encoder[MetadataRequestPayload] = new Encoder[MetadataRequestPayload]:
    override def apply(m: MetadataRequestPayload): Json = Json.obj(
      "name" -> m.name.asJson,
      "description" -> m.description.asJson,
      "owner" -> m.owner.asJson
    )

  given Decoder[MetadataResponsePayload] = new Decoder[MetadataResponsePayload]:
    override def apply(c: HCursor): Result[MetadataResponsePayload] =
      for
        name <- c.downField("name").as[String]
        description <- c.downField("description").as[String]
        author <- c.downField("author").as[String]
        owner <- c.downField("owner").as[String]
      yield MetadataResponsePayload(name, description, author, owner)

  given Decoder[DocumentResponsePayload] = new Decoder[DocumentResponsePayload]:
    override def apply(c: HCursor): Result[DocumentResponsePayload] =
      for
        uuid <- c.downField("uuid").as[DocumentId]
        contentLink <- c.downField("byteStreamUri").as[Uri]
        metadata <- c.downField("metadata").as[MetadataResponsePayload]
      yield DocumentResponsePayload(uuid, metadata, contentLink)

  given Decoder[DocumentIdResponsePayload] = new Decoder[DocumentIdResponsePayload]:
    override def apply(c: HCursor): Result[DocumentIdResponsePayload] =
      c.downField("uuid").as[DocumentId].map(DocumentIdResponsePayload.apply)

trait AuthMiddlewareContext:
  import cats.data.Kleisli
  import org.fiume.sketch.shared.auth0.User
  import org.http4s.server.AuthMiddleware
  import org.http4s.Request
  import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo
  import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo.ErrorMessage
  import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo.ErrorDetails
  import org.http4s.circe.CirceEntityEncoder.*
  import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo.json.given
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
  import org.fiume.sketch.shared.domain.documents.{Document, DocumentId, DocumentWithId, WithStream}
  import org.fiume.sketch.shared.auth0.UserId
  import cats.effect.unsafe.IORuntime

  def makeDocumentsStore(): IO[DocumentsStore[IO, IO]] = makeDocumentsStore(state = Map.empty)

  def makeDocumentsStore(state: DocumentWithIdAndStream[IO]*): IO[DocumentsStore[IO, IO]] =
    makeDocumentsStore(state.map(doc => doc.uuid -> doc).toMap)

  private def makeDocumentsStore(state: Map[DocumentId, DocumentWithIdAndStream[IO]]): IO[DocumentsStore[IO, IO]] =
    Ref.of[IO, Map[DocumentId, DocumentWithIdAndStream[IO]]](state).map { storage =>
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

        def fetchDocument(uuid: DocumentId): IO[Option[DocumentWithId]] =
          storage.get.map(_.collectFirst {
            case (storedUuid, document) if storedUuid === uuid => document
          })

        def documentStream(uuid: DocumentId): IO[Option[fs2.Stream[IO, Byte]]] =
          storage.get.map(_.collectFirst {
            case (storedUuid, document) if storedUuid === uuid => document.stream
          })

        def fetchByAuthor(by: UserId): fs2.Stream[IO, DocumentWithId] =
          fetchAll().filter(_.metadata.author === by)

        def fetchByOwner(by: UserId): fs2.Stream[IO, DocumentWithId] =
          fetchAll().filter(_.metadata.owner === by)

        def delete(uuid: DocumentId): IO[Unit] =
          storage.update { _.removed(uuid) }

        val commit: [A] => IO[A] => IO[A] = [A] => (action: IO[A]) => action

        val lift: [A] => IO[A] => IO[A] = [A] => (action: IO[A]) => action

        given IORuntime = IORuntime.global
        private def fetchAll(): Stream[IO, DocumentWithId] = fs2.Stream.emits(
          storage.get.unsafeRunSync().values.toSeq
        )
    }
