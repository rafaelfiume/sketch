package org.fiume.sketch.storage.documents.http

import cats.data.OptionT
import cats.effect.{IO, Ref}
import cats.effect.unsafe.IORuntime
import cats.implicits.*
import io.circe.syntax.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import munit.Assertions.*
import org.fiume.sketch.shared.app.http4s.middlewares.SemanticInputError
import org.fiume.sketch.shared.app.troubleshooting.{ErrorInfo, ErrorMessage}
import org.fiume.sketch.shared.app.troubleshooting.http.json.ErrorInfoCodecs.given
import org.fiume.sketch.shared.testkit.{ContractContext, Http4sTestingRoutesDsl}
import org.fiume.sketch.shared.testkit.EitherSyntax.*
import org.fiume.sketch.storage.documents.{Document, DocumentWithId}
import org.fiume.sketch.storage.documents.Document.Metadata
import org.fiume.sketch.storage.documents.Document.Metadata.*
import org.fiume.sketch.storage.documents.algebras.DocumentsStore
import org.fiume.sketch.storage.documents.http.DocumentsRoutes.Model.*
import org.fiume.sketch.storage.documents.http.DocumentsRoutes.Model.Json.given
import org.fiume.sketch.storage.testkit.DocumentsGens
import org.fiume.sketch.storage.testkit.DocumentsGens.*
import org.fiume.sketch.storage.testkit.DocumentsGens.given
import org.http4s.{MediaType, *}
import org.http4s.Method.*
import org.http4s.client.dsl.io.*
import org.http4s.headers.`Content-Type`
import org.http4s.implicits.*
import org.http4s.multipart.{Boundary, Multipart, Part}
import org.scalacheck.{Gen, ShrinkLowPriority}
import org.scalacheck.effect.PropF.forAllF

import java.util.UUID

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
    forAllF { (metadata: Metadata) => // TODO Metadata Payload instead
      val multipart = Multipart[IO](
        parts = Vector(
          Part.formData("metadata", metadata.asJson.spaces2SortKeys),
          Part.fileData("bytes", montainBikeInLiguriaImageFile, `Content-Type`(MediaType.image.jpeg))
        ),
        boundary = Boundary("boundary")
      )
      val request = POST(uri"/documents").withEntity(multipart).withHeaders(multipart.headers)
      for
        store <- makeDocumentsStore()
        documentsRoutes <- makeDocumentsRoutes(store)

        jsonResponse <- send(request)
          .to(documentsRoutes.router())
          .expectJsonResponseWith(Status.Created)

        storedMetadata <- store.fetchMetadata(jsonResponse.as[UUID].rightValue)
        uploadedContent <- bytesFrom[IO]("mountain-bike-liguria-ponent.jpg").compile.toList
        storedBytes <- OptionT(
          store.fetchContent(jsonResponse.as[UUID].rightValue)
        ).semiflatMap(_.compile.toList).value
        _ <- IO {
          assertEquals(storedMetadata, metadata.some)
          assertEquals(storedBytes.map(_.toList), uploadedContent.some)
        }
      yield ()
    }

  test("Get document metadata"):
    forAllF { (document: DocumentWithId[IO]) =>
      val request = GET(Uri.unsafeFromString(s"/documents/${document.uuid}/metadata"))
      for
        store <- makeDocumentsStore(state = document)
        documentsRoutes <- makeDocumentsRoutes(store)

        jsonResponse <- send(request)
          .to(documentsRoutes.router())
          .expectJsonResponseWith(Status.Ok)

        _ <- IO {
          assertEquals(jsonResponse.as[Metadata].rightValue, document.metadata)
        }
      yield ()
    }

  test("Get document content"):
    forAllF { (document: DocumentWithId[IO]) =>
      val request = GET(Uri.unsafeFromString(s"/documents/${document.uuid}"))
      for
        store <- makeDocumentsStore(state = document)
        documentsRoutes <- makeDocumentsRoutes(store)

        contentStream <- send(request)
          .to(documentsRoutes.router())
          .expectByteStreamResponseWith(Status.Ok)

        obtainedStream <- contentStream.compile.toList
        expectedStream <- document.content.compile.toList
        _ <- IO {
          assertEquals(obtainedStream, expectedStream)
        }
      yield ()
    }

  test("Delete document"):
    forAllF { (document: DocumentWithId[IO]) =>
      val request = DELETE(Uri.unsafeFromString(s"/documents/${document.uuid}"))
      for
        store <- makeDocumentsStore(state = document)
        documentsRoutes <- makeDocumentsRoutes(store)

        _ <- send(request)
          .to(documentsRoutes.router())
          .expectEmptyResponseWith(Status.NoContent)

        stored <- IO.both(
          store.fetchMetadata(document.uuid),
          OptionT(store.fetchContent(document.uuid)).semiflatMap(_.compile.toList).value
        )
        _ <- IO {
          assertEquals(stored._1, none)
          assertEquals(stored._2, none)
        }
      yield ()
    }

  test("Delete unexistent document == not found"):
    forAllF { (document: DocumentWithId[IO]) =>
      val request = DELETE(Uri.unsafeFromString(s"/documents/${document.uuid}"))
      for
        store <- makeDocumentsStore()
        documentsRoutes <- makeDocumentsRoutes(store)
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
        documentsRoutes <- makeDocumentsRoutes(store)

        request = POST(uri"/documents").withEntity(multipart).withHeaders(multipart.headers)
        result <- send(request)
          .to(documentsRoutes.router())
          .expectJsonResponseWith(Status.UnprocessableEntity)
          .map(_.as[ErrorInfo].rightValue)

        _ <- IO {
          assertEquals(result.message, SemanticInputError.message)
          assert(
            result.details
              .exists {
                _.tips.keySet.subsetOf(
                  Name.invariantErrors
                    .map(_.uniqueCode)
                    .union(Set("missing.document.metadata.part", "missing.document.bytes.part"))
                )
              }
          )
        }
      yield ()
    }

  test("return 422 when document upload request is malformed"):
    forAllF(malformedDocumentRequests) { (multipart: Multipart[IO]) =>
      for
        store <- makeDocumentsStore()
        documentsRoutes <- makeDocumentsRoutes(store)

        request = POST(uri"/documents").withEntity(multipart).withHeaders(multipart.headers)
        result <- send(request)
          .to(documentsRoutes.router())
          .expectJsonResponseWith(Status.UnprocessableEntity)
          .map(_.as[ErrorInfo].rightValue)

        _ <- IO {
          assertEquals(result.message, SemanticInputError.message)
          assert(result.details.get.tips.contains("malformed.document.metadata.payload"))
        }
      yield ()
    }
  /*
   * Contracts
   */

  test("bijective relationship between encoded and decoded Documents.Metadata"):
    assertBijectiveRelationshipBetweenEncoderAndDecoder[Metadata](
      "contract/documents/http/metadata.json"
    )

  test("validation accumulates") {
    /* Also see `given accumulatingParallel: cats.Parallel[EitherT[IO, String, *]] = EitherT.accumulatingParallel` */
    // no metadata part / no bytes part
    val noMultiparts = Multipart[IO](parts = Vector.empty, boundary = Boundary("boundary"))
    for
      inputErrors <- noMultiparts.validated().attempt.map(_.leftValue)

      _ <- IO {
        assert(
          inputErrors.asInstanceOf[SemanticInputError].details.tips.size > 1,
          clue = "errors must accumulate"
        )
      }
    yield ()

  }

trait DocumentsRoutesSpecContext:
  val loggingFlag = false

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
      name <- shortNames.map(Name.notValidatedFromString)
      metadata <- metadataG.map(_.copy(name = name))
    yield Multipart[IO](
      parts = Vector(
        Part.formData("metadata", metadata.asJson.spaces2SortKeys),
        Part.fileData("bytes", montainBikeInLiguriaImageFile, `Content-Type`(MediaType.image.jpeg))
      ),
      boundary = Boundary("boundary")
    )) :| "invalidTooShortDocumentName"

  def makeDocumentsRoutes(withStore: DocumentsStore[IO, IO]): IO[DocumentsRoutes[IO, IO]] =
    IO.delay { new DocumentsRoutes[IO, IO](loggingFlag)(IORuntime.global.compute, withStore) }

trait DocumentsStoreContext:
  import fs2.Stream
  import java.util.UUID

  def makeDocumentsStore(): IO[DocumentsStore[IO, IO]] = makeDocumentsStore(state = Map.empty)

  def makeDocumentsStore(state: DocumentWithId[IO]): IO[DocumentsStore[IO, IO]] =
    makeDocumentsStore(Map(state.uuid -> state))

  private def makeDocumentsStore(state: Map[UUID, DocumentWithId[IO]]): IO[DocumentsStore[IO, IO]] =
    Ref.of[IO, Map[UUID, DocumentWithId[IO]]](state).map { storage =>
      new DocumentsStore[IO, IO]:

        def store(document: Document[IO]): IO[UUID] =
          IO.randomUUID.flatMap { uuid =>
            storage
              .update {
                val documentWithId = Document.withId[IO](uuid, document.metadata, document.content)
                _.updated(uuid, documentWithId)
              }
              .as(uuid)
          }

        def update(document: DocumentWithId[IO]): IO[Unit] =
          storage.update {
            _.updatedWith(document.uuid) {
              case Some(document) =>
                Document.withId[IO](document.uuid, document.metadata, document.content).some
              case None => none
            }
          }.void

        def fetchMetadata(uuid: UUID): IO[Option[Metadata]] =
          storage.get.map(_.collectFirst {
            case (storedUuid, document) if storedUuid === uuid => document.metadata
          })

        def fetchContent(uuid: UUID): IO[Option[fs2.Stream[IO, Byte]]] =
          storage.get.map(_.collectFirst {
            case (storedUuid, document) if storedUuid === uuid => document.content
          })

        def delete(uuid: UUID): IO[Unit] =
          storage.update { _.removed(uuid) }

        val commit: [A] => IO[A] => IO[A] = [A] => (action: IO[A]) => action

        val lift: [A] => IO[A] => IO[A] = [A] => (action: IO[A]) => action
    }
