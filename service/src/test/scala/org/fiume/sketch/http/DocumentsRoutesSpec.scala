package org.fiume.sketch.http

import cats.effect.IO
import cats.implicits.*
import io.circe.syntax.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.fiume.sketch.http.DocumentsRoutes.Model.*
import org.fiume.sketch.http.DocumentsRoutes.Model.json.given
import org.fiume.sketch.shared.auth.User
import org.fiume.sketch.shared.auth.testkit.{AuthMiddlewareContext, UserGens}
import org.fiume.sketch.shared.auth.testkit.UserGens.given
import org.fiume.sketch.shared.authorisation.{AccessControl, ContextualRole}
import org.fiume.sketch.shared.authorisation.testkit.AccessControlContext
import org.fiume.sketch.shared.common.http.middlewares.{SemanticInputError, SemanticValidationMiddleware}
import org.fiume.sketch.shared.common.troubleshooting.ErrorInfo
import org.fiume.sketch.shared.common.troubleshooting.ErrorInfo.{ErrorCode, ErrorMessage}
import org.fiume.sketch.shared.common.troubleshooting.ErrorInfo.json.given
import org.fiume.sketch.shared.common.troubleshooting.syntax.ErrorInfoSyntax.*
import org.fiume.sketch.shared.domain.documents.{Document, DocumentWithIdAndStream}
import org.fiume.sketch.shared.domain.documents.algebras.DocumentsStore
import org.fiume.sketch.shared.domain.testkit.DocumentsGens.*
import org.fiume.sketch.shared.domain.testkit.DocumentsGens.given
import org.fiume.sketch.shared.domain.testkit.DocumentsStoreContext
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
import org.scalacheck.{Arbitrary, Gen, ShrinkLowPriority}
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
    // TODO How to restrict access to posting documents?
    forAllF { (metadata0: Document.Metadata, user: User, randomUser: User) =>
      val metadata = metadata0.copy(ownerId = user.uuid)
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
        stored <- store.fetchDocument(result.uuid)
        grantedAccessToUser <- accessControl.canAccess(user.uuid, result.uuid)
        // TODO it feels that access-control related properties are mixed with other domain properties
        noGrantedAccessToRandomUser <- accessControl.canAccess(randomUser.uuid, result.uuid).map(!_)
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

  test("attempt to access a document without permission results in 403"):
    case class DocumentRequest(httpRequest: Request[IO], documentWithIdAndStream: DocumentWithIdAndStream[IO])
    given Arbitrary[DocumentRequest] = Arbitrary {
      for
        document <- documentWithIdAndStreams
        request <- Gen.oneOf(
          GET(Uri.unsafeFromString(s"/documents/${document.uuid.value}/metadata")),
          GET(Uri.unsafeFromString(s"/documents/${document.uuid.value}")),
          // GET(Uri.unsafeFromString(s"/documents")), // TODO Shouldn't this be forbidden as well?
          DELETE(Uri.unsafeFromString(s"/documents/${document.uuid.value}"))
        )
      yield DocumentRequest(request, document)
    }
    forAllF { (documentRequest: DocumentRequest, authenticated: User) =>
      for
        accessControl <- makeAccessControl()
        // the authenticated user is not the document owner nor a Superuser
        store <- makeDocumentsStore(state = documentRequest.documentWithIdAndStream)
        authMiddleware = makeAuthMiddleware(authenticated)
        documentsRoutes <- makeDocumentsRoutes(authMiddleware, accessControl, store)

        _ <- send(documentRequest.httpRequest)
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
        assertEquals(result.code, "9011".code)
        assertEquals(result.message, "Input data doesn't meet the requirements".message)
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
      yield assertEquals(
        result,
        ErrorInfo.make(
          "9011".code,
          "Input data doesn't meet the requirements".message,
          ("malformed.document.metadata.payload" -> "the metadata payload does not meet the contract").details
        )
      )
    }

  test("MetadataRequestPayload encode and decode form a bijective relationship"):
    assertBijectiveRelationshipBetweenEncoderAndDecoder[MetadataRequestPayload]("domain/documents/get.metadata.request.json")

  test("DocumentResponsePayload encode and decode form a bijective relationship"):
    assertBijectiveRelationshipBetweenEncoderAndDecoder[DocumentResponsePayload]("domain/documents/get.metadata.response.json")

  test("DocumentIdResponsePayload encode and decode form a bijective relationship"):
    assertBijectiveRelationshipBetweenEncoderAndDecoder[DocumentIdResponsePayload]("domain/documents/post.response.json")

  test("validation accumulates") {
    /* Also see `given accumulatingParallel: cats.Parallel[EitherT[IO, String, *]] = EitherT.accumulatingParallel` */
    // no metadata part / no bytes part
    val noMultiparts = Multipart[IO](parts = Vector.empty, boundary = Boundary("boundary"))
    val ownerId = UserGens.userIds.sample.someOrFail
    for inputErrors <- noMultiparts.validated(ownerId).attempt.map(_.leftOrFail)
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
