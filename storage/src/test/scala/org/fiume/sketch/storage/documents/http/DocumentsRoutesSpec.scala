package org.fiume.sketch.storage.documents.http

import cats.data.{EitherT, NonEmptyChain, OptionT}
import cats.effect.{IO, Ref}
import cats.implicits.*
import io.circe.Json
import io.circe.syntax.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import munit.Assertions.*
import org.fiume.sketch.shared.test.{FileContentContext, Http4sTestingRoutesDsl}
import org.fiume.sketch.shared.test.EitherSyntax.*
import org.fiume.sketch.storage.documents.Model.{Document, Metadata}
import org.fiume.sketch.storage.documents.algebras.DocumentsStore
import org.fiume.sketch.storage.documents.http.DocumentsRoutes.*
import org.fiume.sketch.storage.documents.http.JsonCodecs.given
import org.fiume.sketch.storage.http.JsonCodecs.Incorrects.given
import org.fiume.sketch.storage.http.Model.Incorrect
import org.fiume.sketch.storage.http.Model.IncorrectOps.*
import org.fiume.sketch.test.support.DocumentsGens.*
import org.fiume.sketch.test.support.DocumentsGens.given
import org.http4s.{MediaType, *}
import org.http4s.Method.*
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.client.dsl.io.*
import org.http4s.headers.`Content-Type`
import org.http4s.implicits.*
import org.http4s.multipart.{Boundary, Multipart, Part}
import org.scalacheck.{Shrink, ShrinkLowPriority}
import org.scalacheck.effect.PropF.forAllF

import java.util.UUID

class DocumentsRoutesSpec
    extends CatsEffectSuite
    with ScalaCheckEffectSuite
    with Http4sTestingRoutesDsl
    with FileContentContext
    with DocumentsStoreContext
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
          .to(new DocumentsRoutes[IO, IO](store).routes)
          .expectJsonResponseWith(Status.Created)

        storedMetadata <- store.fetchMetadata(jsonResponse.as[UUID].rightValue)
        uploadedContent <- bytesFrom[IO]("mountain-bike-liguria-ponent.jpg").compile.toList
        storedBytes <- OptionT(
          store.fetchBytes(jsonResponse.as[UUID].rightValue)
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
          .to(new DocumentsRoutes[IO, IO](store).routes)
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
          .to(new DocumentsRoutes[IO, IO](store).routes)
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
          .to(new DocumentsRoutes[IO, IO](store).routes)
          .expectEmptyResponseWith(Status.NoContent)

        stored <- IO.both(
          store.fetchMetadata(document.uuid),
          OptionT(store.fetchBytes(document.uuid)).semiflatMap(_.compile.toList).value
        )
        _ <- IO {
          assertEquals(stored._1, none)
          assertEquals(stored._2, none)
        }
      yield ()
    }
  }

  /* Sad Path */

  test("Post document with no file == bad request") {
    forAllF(metadataG) { metadata =>
      val multipart = Multipart[IO](
        // no file mamma!
        parts = Vector(Part.formData("metadata", metadata.asJson.spaces2SortKeys)),
        boundary = Boundary("boundary")
      )
      val request = POST(uri"/documents").withEntity(multipart).withHeaders(multipart.headers)
      for
        store <- makeDocumentsStore()

        jsonResponse <- send(request)
          .to(new DocumentsRoutes[IO, IO](store).routes)
          .expectJsonResponseWith(Status.BadRequest)

        _ <- IO {
          assertEquals(jsonResponse.as[Incorrect].rightValue, Incorrect("bytes".missing))
        }
      yield ()
    }
  }

  // TODO Can be merged with test above
  test("Post document with no metadata == bad request") {
    val imageFile = getClass.getClassLoader.getResource("mountain-bike-liguria-ponent.jpg")
    val multipart = Multipart[IO](
      // no metadata mamma!
      parts = Vector(Part.fileData("bytes", imageFile, `Content-Type`(MediaType.image.jpeg))),
      boundary = Boundary("boundary")
    )
    val request = POST(uri"/documents").withEntity(multipart).withHeaders(multipart.headers)
    for
      store <- makeDocumentsStore()

      jsonResponse <- send(request)
        .to(new DocumentsRoutes[IO, IO](store).routes)
        .expectJsonResponseWith(Status.BadRequest)

      _ <- IO {
        assertEquals(jsonResponse.as[Incorrect].rightValue, Incorrect("metadata".missing))
      }
    yield ()
  }

  test("Post document with malformed metadata == bad request") {
    val imageFile = getClass.getClassLoader.getResource("mountain-bike-liguria-ponent.jpg")
    val multipart = Multipart[IO](
      parts = Vector(
        Part.formData("metadata", """ { \"bananas\" : \"apples\" } """),
        Part.fileData("bytes", imageFile, `Content-Type`(MediaType.image.jpeg))
      ),
      boundary = Boundary("boundary")
    )
    val request = POST(uri"/documents").withEntity(multipart).withHeaders(multipart.headers)
    for
      store <- makeDocumentsStore()

      jsonResponse <- send(request)
        .to(new DocumentsRoutes[IO, IO](store).routes)
        .expectJsonResponseWith(Status.BadRequest)

      _ <- IO {
        assertEquals(jsonResponse.as[Incorrect].rightValue, Incorrect("Malformed message body: Invalid JSON".malformed))
      }
    yield ()
  }

  test("Delete unexistent document == not found") {
    forAllF(documents[IO]) { document =>
      val request = DELETE(Uri.unsafeFromString(s"/documents/${document.uuid}"))
      for
        store <- makeDocumentsStore()
        _ <- send(request)
          .to(new DocumentsRoutes[IO, IO](store).routes)
          .expectEmptyResponseWith(Status.NotFound)
      yield ()
    }
  }

  /* Validation */

  List(
    "missing" ->
      Multipart[IO](parts = Vector.empty, boundary = Boundary("boundary")) ->
      "metadata".missing,
    "malformed" ->
      Multipart[IO](
        parts = Vector(Part.formData("metadata", """ { \"bananas\" : \"apples\" } """)),
        boundary = Boundary("boundary")
      ) -> "Malformed message body: Invalid JSON".malformed
  )
    .foreach { case ((description, multipart), expected) =>
      test(s"validate metadata: $description") {
        multipart.metadata
          .map { _ => fail("expected left") }
          .leftMap { result => assertEquals(result, expected) }
          .value
      }
    }

  List(
    "missing" -> Multipart[IO](parts = Vector.empty, boundary = Boundary("boundary")) -> "bytes".missing
  )
    .foreach { case ((description, multipart), expected) =>
      test(s"validate document bytes: $description") {
        multipart.bytes
          .map { _ => fail("expected left") }
          .leftMap { result => assertEquals(result, expected) }
          .value
      }
    }

  test("validation accumulates") {
    /*
     * Needs an alternative instance of Parallel to accumulate error
     * More details here: https://github.com/typelevel/cats/pull/3777/files
     */
    given accumulatingParallel: cats.Parallel[EitherT[IO, NonEmptyChain[Incorrect.Detail], *]] =
      EitherT.accumulatingParallel

    val multipart = Multipart[IO](
      parts = Vector.empty,
      boundary = Boundary("boundary")
    )

    val result: EitherT[IO, NonEmptyChain[Incorrect.Detail], (Metadata, fs2.Stream[IO, Byte])] =
      (multipart.metadata, multipart.bytes).parTupled

    result
      .map { _ => fail("expected left") }
      .leftMap { result => assertEquals(result.toList, ("metadata".missing |+| "bytes".missing).toList) }
      .value
  }

trait DocumentsStoreContext:
  import fs2.Stream
  import java.time.ZonedDateTime
  import java.time.ZoneOffset
  import java.util.UUID

  def makeDocumentsStore(): IO[DocumentsStore[IO, IO]] = makeDocumentsStore(state = StorageState.empty)

  def makeDocumentsStore(state: Document[IO]): IO[DocumentsStore[IO, IO]] = makeDocumentsStore(
    StorageState(
      allDocuments = Map(state.uuid -> state),
      latestStored = state.uuid.some
    )
  )

  case class StorageState(allDocuments: Map[UUID, Document[IO]], latestStored: Option[UUID])
  object StorageState:
    def empty: StorageState = StorageState(Map.empty, None)

  private def makeDocumentsStore(state: StorageState): IO[DocumentsStore[IO, IO]] =

    def newDocument(metadata: Metadata, bytes: Stream[IO, Byte]): Document[IO] = // not suspending side effect
      Document[IO](UUID.randomUUID(), metadata, bytes, ZonedDateTime.now(ZoneOffset.UTC), ZonedDateTime.now(ZoneOffset.UTC))

    Ref.of[IO, StorageState](state).map { storage =>
      new DocumentsStore[IO, IO]:

        def store(metadata: Metadata, content: Stream[IO, Byte]): IO[UUID] = storage
          .updateAndGet { state =>
            val document = newDocument(metadata, content)
            StorageState(state.allDocuments.updated(document.uuid, document), document.uuid.some)
          }
          .map(_.latestStored.get)

        def update(uuid: UUID, metadata: Metadata, content: Stream[cats.effect.IO, Byte]): IO[Unit] = storage.update { state =>
          StorageState(
            state.allDocuments.updatedWith(uuid) {
              case Some(document) =>
                Document[IO](uuid, metadata, content, document.createdAt, ZonedDateTime.now(ZoneOffset.UTC)).some
              case None => none
            },
            none
          )
        }.void

        def fetchMetadata(uuid: UUID): IO[Option[Metadata]] = storage.get.map { state =>
          state.allDocuments.find(s => s._1 === uuid).map(_._2.metadata)
        }

        def fetchBytes(uuid: UUID): IO[Option[fs2.Stream[IO, Byte]]] = storage.get.map { state =>
          state.allDocuments.find(s => s._1 === uuid).map(_._2.bytes)
        }

        def delete(uuid: UUID): IO[Unit] = storage.update { state =>
          StorageState(state.allDocuments.removed(uuid), none)
        }

        val commit: [A] => IO[A] => IO[A] = [A] => (action: IO[A]) => action

        val lift: [A] => IO[A] => IO[A] = [A] => (action: IO[A]) => action
    }
