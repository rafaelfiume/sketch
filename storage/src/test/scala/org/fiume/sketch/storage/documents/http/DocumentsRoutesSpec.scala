package org.fiume.sketch.storage.documents.http

import cats.data.{EitherT, NonEmptyChain, OptionT}
import cats.effect.{IO, Ref}
import cats.implicits.*
import io.circe.Json
import io.circe.syntax.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import munit.Assertions.*
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo.{ErrorCode, ErrorDetails, ErrorMessage}
import org.fiume.sketch.shared.app.troubleshooting.http.JsonCodecs.ErrorInfoCodecs.given
import org.fiume.sketch.shared.test.{ContractContext, FileContentContext, Http4sTestingRoutesDsl}
import org.fiume.sketch.shared.test.EitherSyntax.*
import org.fiume.sketch.storage.documents.Model.{Document, Metadata}
import org.fiume.sketch.storage.documents.algebras.DocumentsStore
import org.fiume.sketch.storage.documents.http.DocumentsRoutes.*
import org.fiume.sketch.storage.documents.http.JsonCodecs.given
import org.fiume.sketch.test.support.DocumentsGens.*
import org.fiume.sketch.test.support.DocumentsGens.given
import org.http4s.{MediaType, *}
import org.http4s.Method.*
import org.http4s.circe.CirceEntityDecoder.*
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

  test("Post document") {
    forAllF { (metadata: Metadata) =>
      val imageFile = getClass.getClassLoader.getResource("mountain-bike-liguria-ponent.jpg")
      val multipart = Multipart[IO](
        parts = Vector(
          Part.formData("metadata", metadata.asJson.spaces2SortKeys),
          Part.fileData("bytes", imageFile, `Content-Type`(MediaType.image.jpeg))
        ),
        boundary = Boundary("boundary")
      )
      val request = POST(uri"/documents").withEntity(multipart).withHeaders(multipart.headers)
      for
        store <- makeDocumentsStore()

        jsonResponse <- send(request)
          .to(new DocumentsRoutes[IO, IO](store).router())
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
  }

  test("Get document metadata") {
    forAllF(documents[IO]) { document =>
      val request = GET(Uri.unsafeFromString(s"/documents/${document.uuid}/metadata"))
      for
        store <- makeDocumentsStore(state = document)

        jsonResponse <- send(request)
          .to(new DocumentsRoutes[IO, IO](store).router())
          .expectJsonResponseWith(Status.Ok)

        _ <- IO {
          assertEquals(jsonResponse.as[Metadata].rightValue, document.metadata)
        }
      yield ()
    }
  }

  test("Get document content") {
    forAllF(documents[IO]) { document =>
      val request = GET(Uri.unsafeFromString(s"/documents/${document.uuid}/content"))
      for
        store <- makeDocumentsStore(state = document)

        contentStream <- send(request)
          .to(new DocumentsRoutes[IO, IO](store).router())
          .expectByteStreamResponseWith(Status.Ok)

        obtainedStream <- contentStream.compile.toList
        expectedStream <- document.bytes.compile.toList
        _ <- IO {
          assertEquals(obtainedStream, expectedStream)
        }
      yield ()
    }
  }

  test("Delete document") {
    forAllF(documents[IO]) { document =>
      val request = DELETE(Uri.unsafeFromString(s"/documents/${document.uuid}"))
      for
        store <- makeDocumentsStore(state = document)

        _ <- send(request)
          .to(new DocumentsRoutes[IO, IO](store).router())
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
  }

  /* Sad Path */

  test("Post document with malformed metadata == bad request") {
    forAllF(invalidDocumentRequests) { multipart =>
      val request = POST(uri"/documents").withEntity(multipart).withHeaders(multipart.headers)
      for
        store <- makeDocumentsStore()

        result <- send(request)
          .to(new DocumentsRoutes[IO, IO](store).router())
          .expectJsonResponseWith(Status.BadRequest)
          .map(_.as[ErrorInfo].rightValue)

        _ <- IO {
          assertEquals(result.code, ErrorCode.InvalidDocument)
          assertEquals(result.message, ErrorMessage("The provided document is invalid."))
          val allInputErrors = DocumentsRoutes.invalidDocuments.map(_.uniqueCode)
          val actualInputErrors = result.details.get.values.keys.toSet
          assert(actualInputErrors.subsetOf(allInputErrors),
                 clue = s"actualInputErrors: $actualInputErrors\nallInputErrors: $allInputErrors"
          )
        }
      yield ()
    }
  }

  test("Delete unexistent document == not found") {
    forAllF(documents[IO]) { document =>
      val request = DELETE(Uri.unsafeFromString(s"/documents/${document.uuid}"))
      for
        store <- makeDocumentsStore()
        _ <- send(request)
          .to(new DocumentsRoutes[IO, IO](store).router())
          .expectEmptyResponseWith(Status.NotFound)
      yield ()
    }
  }

  /*
   * Contracts
   */

  test("bijective relationship between encoded and decoded Documents.Metadata"):
    assertBijectiveRelationshipBetweenEncoderAndDecoder[Metadata](
      "contract/documents/http/metadata.json"
    )

  test("validation accumulates") {
    /*
     * Needs an alternative instance of Parallel to accumulate error
     * More details here: https://github.com/typelevel/cats/pull/3777/files
     */
    given accumulatingParallel: cats.Parallel[EitherT[IO, NonEmptyChain[DocumentsRoutes.InvalidDocument], *]] =
      EitherT.accumulatingParallel

    val multipart = Multipart[IO](
      parts = Vector.empty,
      boundary = Boundary("boundary")
    )
    val result: EitherT[IO, NonEmptyChain[DocumentsRoutes.InvalidDocument], (Metadata, fs2.Stream[IO, Byte])] =
      (multipart.metadata, multipart.bytes).parTupled

    result
      .leftMap { result => assertEquals(result.toList, NonEmptyChain.of(MissingMetadata, MissingContent).toList) }
      .value
  }

trait DocumentsRoutesSpecContext:
  def invalidDocumentRequests: Gen[Multipart[IO]] = Gen.oneOf(
    invalidDocumentRequestWithNoContent,
    invalidMultipartsWithNoMetadata,
    invalidDocumentRequestWithMalformedMetadata
  )

  // TODO What if document name is empty?

  def invalidDocumentRequestWithNoContent: Gen[Multipart[IO]] = metadataG.flatMap { metadata =>
    Gen.delay {
      Multipart[IO](
        // no file mamma!
        parts = Vector(Part.formData("metadata", metadata.asJson.spaces2SortKeys)),
        boundary = Boundary("boundary")
      )
    }
  }

  def invalidMultipartsWithNoMetadata: Gen[Multipart[IO]] = Gen.delay {
    val imageFile = getClass.getClassLoader.getResource("mountain-bike-liguria-ponent.jpg")
    Multipart[IO](
      // no metadata mamma!
      parts = Vector(Part.fileData("bytes", imageFile, `Content-Type`(MediaType.image.jpeg))),
      boundary = Boundary("boundary")
    )
  }

  def invalidDocumentRequestWithMalformedMetadata: Gen[Multipart[IO]] = Gen.delay {
    val imageFile = getClass.getClassLoader.getResource("mountain-bike-liguria-ponent.jpg")
    Multipart[IO](
      parts = Vector(
        Part.formData("metadata", """ { \"bananas\" : \"apples\" } """),
        Part.fileData("bytes", imageFile, `Content-Type`(MediaType.image.jpeg))
      ),
      boundary = Boundary("boundary")
    )
  }

trait DocumentsStoreContext:
  import fs2.Stream
  import java.time.ZonedDateTime
  import java.time.ZoneOffset
  import java.util.UUID

  def makeDocumentsStore(): IO[DocumentsStore[IO, IO]] = makeDocumentsStore(state = Map.empty)

  def makeDocumentsStore(state: Document[IO]): IO[DocumentsStore[IO, IO]] = makeDocumentsStore(Map(state.uuid -> state))

  private def makeDocumentsStore(state: Map[UUID, Document[IO]]): IO[DocumentsStore[IO, IO]] =
    Ref.of[IO, Map[UUID, Document[IO]]](state).map { storage =>
      new DocumentsStore[IO, IO]:

        def store(metadata: Metadata, content: Stream[IO, Byte]): IO[UUID] =
          IO.randomUUID.flatMap { uuid =>
            storage
              .update {
                val document =
                  new Document[IO](uuid, metadata, content, ZonedDateTime.now(ZoneOffset.UTC), ZonedDateTime.now(ZoneOffset.UTC))
                _.updated(document.uuid, document)
              }
              .as(uuid)
          }

        def update(uuid: UUID, metadata: Metadata, content: Stream[cats.effect.IO, Byte]): IO[Unit] =
          storage.update {
            _.updatedWith(uuid) {
              case Some(document) =>
                Document[IO](uuid, metadata, content, document.createdAt, ZonedDateTime.now(ZoneOffset.UTC)).some
              case None => none
            }
          }.void

        def fetchMetadata(uuid: UUID): IO[Option[Metadata]] =
          storage.get.map(_.collectFirst {
            case (storedUuid, document) if storedUuid === uuid => document.metadata
          })

        def fetchContent(uuid: UUID): IO[Option[fs2.Stream[IO, Byte]]] =
          storage.get.map(_.collectFirst {
            case (storedUuid, document) if storedUuid === uuid => document.bytes
          })

        def delete(uuid: UUID): IO[Unit] =
          storage.update { _.removed(uuid) }

        val commit: [A] => IO[A] => IO[A] = [A] => (action: IO[A]) => action

        val lift: [A] => IO[A] => IO[A] = [A] => (action: IO[A]) => action
    }
