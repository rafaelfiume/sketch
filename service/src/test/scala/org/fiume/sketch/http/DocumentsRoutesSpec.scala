package org.fiume.sketch.http

import cats.effect.{IO, Ref}
import cats.implicits.*
import io.circe.{Decoder, Encoder, HCursor, Json}
import io.circe.Decoder.Result
import io.circe.syntax.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import munit.Assertions.*
import org.fiume.sketch.authorisation.{AccessControl, ContextualRole}
import org.fiume.sketch.authorisation.testkit.AccessControlContext
import org.fiume.sketch.http.DocumentsRoutes.Model.*
import org.fiume.sketch.http.DocumentsRoutes.Model.json.given
import org.fiume.sketch.shared.app.WithUuid
import org.fiume.sketch.shared.app.http4s.JsonCodecs.given
import org.fiume.sketch.shared.app.http4s.middlewares.{SemanticInputError, SemanticValidationMiddleware}
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo.json.given
import org.fiume.sketch.shared.auth0.User
import org.fiume.sketch.shared.auth0.testkit.AuthMiddlewareContext
import org.fiume.sketch.shared.auth0.testkit.UserGens.given
import org.fiume.sketch.shared.domain.documents.{Document, DocumentId, DocumentWithIdAndStream, DocumentWithStream}
import org.fiume.sketch.shared.domain.documents.algebras.DocumentsStore
import org.fiume.sketch.shared.domain.testkit.DocumentsGens.*
import org.fiume.sketch.shared.domain.testkit.DocumentsGens.given
import org.fiume.sketch.shared.testkit.{ContractContext, Http4sRoutesContext}
import org.fiume.sketch.shared.testkit.syntax.EitherSyntax.*
import org.fiume.sketch.shared.testkit.syntax.OptionSyntax.*
import org.http4s.{MediaType, *}
import org.http4s.Method.*
import org.http4s.client.dsl.io.*
import org.http4s.headers.`Content-Type`
import org.http4s.implicits.*
import org.http4s.multipart.{Boundary, Multipart, Part}
import org.http4s.server.AuthMiddleware
import org.scalacheck.{Gen, ShrinkLowPriority}
import org.scalacheck.effect.PropF.forAllF

class DocumentsRoutesSpec
    extends CatsEffectSuite
    with ScalaCheckEffectSuite
    with Http4sRoutesContext
    with AuthMiddlewareContext
    with AccessControlContext
    with DocumentsStoreContext
    with ContractContext
    with DocumentsRoutesSpecContext
    with ShrinkLowPriority:

  override def scalaCheckTestParameters = super.scalaCheckTestParameters.withMinSuccessfulTests(10)

  test("uploads document"):
    forAllF { (metadata: Document.Metadata, user: User, randomUser: User) =>
      val multipart = Multipart[IO](
        parts = Vector(
          Part.formData("metadata", metadata.asRequestPayload.asJson.spaces2SortKeys),
          Part.fileData("bytes", montainBikeInLiguriaImageFile, `Content-Type`(MediaType.image.jpeg))
        ),
        boundary = Boundary("boundary")
      )
      val request = POST(uri"/documents").withEntity(multipart).withHeaders(multipart.headers)
      for
        accessControl <- makeAccessControl()
        store <- makeDocumentsStore()
        authMiddleware = makeAuthMiddleware(authenticated = user)
        documentsRoutes <- makeDocumentsRoutes(authMiddleware, accessControl, store)

        result <- send(request)
          .to(documentsRoutes.router())
//
          .expectJsonResponseWith[DocumentIdResponsePayload](Status.Created)
        stored <- store.fetchDocument(result.value)
        grantedAccessToUser <- accessControl.canAccess(user.uuid, result.value)
        noGrantedAccessToRandomUser <- accessControl.canAccess(randomUser.uuid, result.value).map(!_)
      yield
        assertEquals(stored.map(_.metadata), metadata.some)
        assert(grantedAccessToUser)
        assert(noGrantedAccessToRandomUser)
    }

  test("retrieves metadata of stored document"):
    forAllF { (document: DocumentWithIdAndStream[IO], user: User) =>
      val request = GET(Uri.unsafeFromString(s"/documents/${document.uuid.value}/metadata"))
      for
        accessControl <- makeAccessControl()
        _ <- accessControl.grantAccess(user.uuid, document.uuid, ContextualRole.Owner)
        store <- makeDocumentsStore(state = document)
        authMiddleware = makeAuthMiddleware(authenticated = user)
        documentsRoutes <- makeDocumentsRoutes(authMiddleware, accessControl, store)

        result <- send(request)
          .to(documentsRoutes.router())
//
          .expectJsonResponseWith[DocumentResponsePayload](Status.Ok)
      yield assertEquals(result, document.asResponsePayload)
    }

  test("retrieves content bytes of stored document"):
    forAllF { (document: DocumentWithIdAndStream[IO], user: User) =>
      val request = GET(Uri.unsafeFromString(s"/documents/${document.uuid.value}"))
      for
        accessControl <- makeAccessControl()
        _ <- accessControl.grantAccess(user.uuid, document.uuid, ContextualRole.Owner)
        store <- makeDocumentsStore(state = document)
        authMiddleware = makeAuthMiddleware(authenticated = user)
        documentsRoutes <- makeDocumentsRoutes(authMiddleware, accessControl, store)

        result <- send(request)
          .to(documentsRoutes.router())
//
          .expectByteStreamResponseWith(Status.Ok)
        obtainedStream <- result.compile.toList
        expectedStream <- document.stream.compile.toList
      yield assertEquals(obtainedStream, expectedStream)
    }

  test("retrieves documents of which the user is the owner"):
    forAllF { (fstDoc: DocumentWithIdAndStream[IO], sndDoc: DocumentWithIdAndStream[IO], user: User) =>
      val request = GET(Uri.unsafeFromString("/documents"))
      for
        accessControl <- makeAccessControl()
        _ <- accessControl.grantAccess(user.uuid, sndDoc.uuid, ContextualRole.Owner)
        store <- makeDocumentsStore(state = fstDoc, sndDoc)
        authMiddleware = makeAuthMiddleware(authenticated = user)
        documentsRoutes <- makeDocumentsRoutes(authMiddleware, accessControl, store)

        result <- send(request)
          .to(documentsRoutes.router())
//
          .expectJsonResponseWith[DocumentResponsePayload](Status.Ok)
      yield assertEquals(result, sndDoc.asResponsePayload)
    }

  test("deletes stored document"):
    forAllF { (document: DocumentWithIdAndStream[IO], user: User) =>
      val request = DELETE(Uri.unsafeFromString(s"/documents/${document.uuid.value}"))
      for
        accessControl <- makeAccessControl()
        _ <- accessControl.grantAccess(user.uuid, document.uuid, ContextualRole.Owner)
        store <- makeDocumentsStore(state = document)
        authMiddleware = makeAuthMiddleware(authenticated = user)
        documentsRoutes <- makeDocumentsRoutes(authMiddleware, accessControl, store)

        _ <- send(request)
          .to(documentsRoutes.router())
//
          .expectEmptyResponseWith(Status.NoContent)
        result <- store.fetchDocument(document.uuid)
        grantRemoved <- accessControl.canAccess(user.uuid, document.uuid).map(!_)
      yield
        assertEquals(result, none)
        assert(grantRemoved)
    }

  /* Sad Path */

  /**
   * Authorisation
   */

  test("attempt to retrieve metadata of a document without access results in 403 Forbidden"):
    forAllF { (document: DocumentWithIdAndStream[IO], authenticated: User) =>
      val request = GET(Uri.unsafeFromString(s"/documents/${document.uuid.value}/metadata"))
      for
        accessControl <- makeAccessControl()
        // the authenticated user is not the document owner
        store <- makeDocumentsStore(state = document)
        authMiddleware = makeAuthMiddleware(authenticated)
        documentsRoutes <- makeDocumentsRoutes(authMiddleware, accessControl, store)

        _ <- send(request)
          .to(documentsRoutes.router())
//
          .expectEmptyResponseWith(Status.Forbidden)
      yield ()
    }

  test("attempt to retrieve content bytes of a document without access results in 403 Forbidden"):
    forAllF { (document: DocumentWithIdAndStream[IO], authenticated: User) =>
      val request = GET(Uri.unsafeFromString(s"/documents/${document.uuid.value}"))
      for
        accessControl <- makeAccessControl()
        // the authenticated user is not the document owner
        store <- makeDocumentsStore(state = document)
        authMiddleware = makeAuthMiddleware(authenticated)
        documentsRoutes <- makeDocumentsRoutes(authMiddleware, accessControl, store)

        _ <- send(request)
          .to(documentsRoutes.router())
//
          .expectEmptyResponseWith(Status.Forbidden)
      yield ()
    }

  test("attempt to delete a document without access results in 403 Forbidden"):
    forAllF { (document: DocumentWithIdAndStream[IO], authenticated: User) =>
      val request = DELETE(Uri.unsafeFromString(s"/documents/${document.uuid.value}"))
      for
        accessControl <- makeAccessControl()
        // the authenticated user is not the document owner
        store <- makeDocumentsStore(state = document)
        authMiddleware = makeAuthMiddleware(authenticated)
        documentsRoutes <- makeDocumentsRoutes(authMiddleware, accessControl, store)

        _ <- send(request)
          .to(documentsRoutes.router())
//
          .expectEmptyResponseWith(Status.Forbidden)
      yield ()
    }

  /**
   * Others
   */

  test("semantically invalid upload request results in 422 Unprocessable Entity"):
    forAllF(semanticallyInvalidDocumentRequests) { (multipart: Multipart[IO]) =>
      for
        accessControl <- makeAccessControl()
        store <- makeDocumentsStore()
        authMiddleware = makeAuthMiddleware()
        documentsRoutes <- makeDocumentsRoutes(authMiddleware, accessControl, store)

        request = POST(uri"/documents").withEntity(multipart).withHeaders(multipart.headers)
        result <- send(request)
          .to(SemanticValidationMiddleware(documentsRoutes.router()))
          .expectJsonResponseWith[ErrorInfo](Status.UnprocessableEntity)
//
      yield
        assertEquals(result.message, SemanticInputError.message)
        assert(
          result.details.someOrFail.tips.keySet.subsetOf(
            Set("missing.document.metadata.part", "missing.document.bytes.part", "document.name.too.short")
          ),
          clue = result.details.someOrFail.tips.mkString
        )
    }

  test("malformed upload request results in 422 Unprocessable Entity"):
    forAllF(malformedDocumentRequests) { (multipart: Multipart[IO]) =>
      for
        accessControl <- makeAccessControl()
        store <- makeDocumentsStore()
        authMiddleware = makeAuthMiddleware()
        documentsRoutes <- makeDocumentsRoutes(authMiddleware, accessControl, store)

        request = POST(uri"/documents").withEntity(multipart).withHeaders(multipart.headers)
        result <- send(request)
          .to(SemanticValidationMiddleware(documentsRoutes.router()))
          .expectJsonResponseWith[ErrorInfo](Status.UnprocessableEntity)
//
      yield
        assertEquals(result.message, SemanticInputError.message)
        assertEquals(result.details.someOrFail.tips,
                     Map("malformed.document.metadata.payload" -> "the metadata payload does not meet the contract")
        )
    }

  /*
   * Contracts
   */

  test("MetadataRequestPayload encode and decode form a bijective relationship"):
    assertBijectiveRelationshipBetweenEncoderAndDecoder[MetadataRequestPayload]("document/metadata.request.json")

  test("DocumentResponsePayload encode and decode form a bijective relationship"):
    assertBijectiveRelationshipBetweenEncoderAndDecoder[DocumentResponsePayload]("document/response.json")

  test("DocumentIdResponsePayload encode and decode form a bijective relationship"):
    assertBijectiveRelationshipBetweenEncoderAndDecoder[DocumentIdResponsePayload]("document/uuid.response.json")

  test("validation accumulates") {
    /* Also see `given accumulatingParallel: cats.Parallel[EitherT[IO, String, *]] = EitherT.accumulatingParallel` */
    // no metadata part / no bytes part
    val noMultiparts = Multipart[IO](parts = Vector.empty, boundary = Boundary("boundary"))
    for inputErrors <- noMultiparts.validated().attempt.map(_.leftOrFail)
    yield assert(
      inputErrors.asInstanceOf[SemanticInputError].details.tips.size === 2,
      clue = inputErrors
    )

  }

trait DocumentsRoutesSpecContext:

  def montainBikeInLiguriaImageFile = getClass.getClassLoader.getResource("mountain-bike-liguria-ponent.jpg")

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

  private def invalidPartWithNoContent: Gen[Multipart[IO]] = metadataG.flatMap { metadata =>
    Gen.delay {
      Multipart[IO](
        // no file mamma!
        parts = Vector(Part.formData("metadata", metadata.asRequestPayload.asJson.spaces2SortKeys)),
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
      metadata <- metadataG.map(_.asRequestPayload.copy(name = name))
    yield Multipart[IO](
      parts = Vector(
        Part.formData("metadata", metadata.asJson.spaces2SortKeys),
        Part.fileData("bytes", montainBikeInLiguriaImageFile, `Content-Type`(MediaType.image.jpeg))
      ),
      boundary = Boundary("boundary")
    )) :| "invalidTooShortDocumentName"

  def makeDocumentsRoutes(
    authMiddleware: AuthMiddleware[IO, User],
    accessControl: AccessControl[IO, IO],
    withStore: DocumentsStore[IO, IO]
  ): IO[DocumentsRoutes[IO, IO]] =
    val documentBytesSizeLimit = 5 * 1024 * 1024
    IO.delay { new DocumentsRoutes[IO, IO](authMiddleware, documentBytesSizeLimit, accessControl, withStore) }

  extension (m: Document.Metadata)
    def asRequestPayload: MetadataRequestPayload =
      MetadataRequestPayload(m.name.value, m.description.value)

  given Encoder[MetadataRequestPayload] = new Encoder[MetadataRequestPayload]:
    override def apply(m: MetadataRequestPayload): Json = Json.obj(
      "name" -> m.name.asJson,
      "description" -> m.description.asJson
    )

  given Decoder[MetadataResponsePayload] = new Decoder[MetadataResponsePayload]:
    override def apply(c: HCursor): Result[MetadataResponsePayload] =
      for
        name <- c.downField("name").as[String]
        description <- c.downField("description").as[String]
      yield MetadataResponsePayload(name, description)

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

trait DocumentsStoreContext:
  import fs2.Stream
  import org.fiume.sketch.shared.domain.documents.{Document, DocumentId, DocumentWithId, WithStream}
  import cats.effect.unsafe.IORuntime

  def makeDocumentsStore(): IO[DocumentsStore[IO, IO]] = makeDocumentsStore(state = Map.empty)

  def makeDocumentsStore(state: DocumentWithIdAndStream[IO]*): IO[DocumentsStore[IO, IO]] =
    makeDocumentsStore(state.map(doc => doc.uuid -> doc).toMap)

  private def makeDocumentsStore(state: Map[DocumentId, DocumentWithIdAndStream[IO]]): IO[DocumentsStore[IO, IO]] =
    Ref.of[IO, Map[DocumentId, DocumentWithIdAndStream[IO]]](state).map { storage =>
      new DocumentsStore[IO, IO]:
        override def store(document: DocumentWithStream[IO]): IO[DocumentId] =
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

        override def fetchDocument(uuid: DocumentId): IO[Option[DocumentWithId]] =
          storage.get.map(_.collectFirst {
            case (storedUuid, document) if storedUuid === uuid => document
          })

        override def documentStream(uuid: DocumentId): fs2.Stream[IO, Byte] =
          fetchAll().find { _.uuid === uuid }.flatMap(_.stream)

        override def fetchDocuments(uuids: fs2.Stream[IO, DocumentId]): fs2.Stream[IO, DocumentWithId] =
          uuids.flatMap { uuid => fetchAll().find(_.uuid === uuid) }

        override def delete(uuid: DocumentId): IO[Unit] = storage.update { _.removed(uuid) }

        override val lift: [A] => IO[A] => IO[A] = [A] => (action: IO[A]) => action

        override val commit: [A] => IO[A] => IO[A] = [A] => (action: IO[A]) => action

        override val commitStream: [A] => fs2.Stream[IO, A] => fs2.Stream[IO, A] = [A] => (action: fs2.Stream[IO, A]) => action

        given IORuntime = IORuntime.global
        private def fetchAll(): Stream[IO, DocumentWithIdAndStream[IO]] = fs2.Stream.emits(
          storage.get.unsafeRunSync().values.toSeq
        )
    }
