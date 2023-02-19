package org.fiume.sketch.datastore.http

import cats.data.{EitherT, NonEmptyChain, OptionT}
import cats.effect.{IO, Ref}
import cats.implicits.*
import io.circe.syntax.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.fiume.sketch.datastore.algebras.DocumentsStore
import org.fiume.sketch.datastore.http.DocumentsRoutes
import org.fiume.sketch.datastore.http.JsonCodecs.Documents.given
import org.fiume.sketch.datastore.http.JsonCodecs.Incorrects.given
import org.fiume.sketch.datastore.http.Model.Incorrect
import org.fiume.sketch.datastore.http.Model.IncorrectOps.*
import org.fiume.sketch.domain.Document
import org.fiume.sketch.support.{FileContentContext, Http4sTestingRoutesDsl}
import org.fiume.sketch.support.EitherSyntax.*
import org.fiume.sketch.support.gens.SketchGens.Documents.*
import org.http4s.{MediaType, *}
import org.http4s.Method.*
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.client.dsl.io.*
import org.http4s.headers.`Content-Type`
import org.http4s.implicits.*
import org.http4s.multipart.{Boundary, Multipart, Part}
import org.scalacheck.Shrink
import org.scalacheck.effect.PropF.forAllF

import DocumentsRoutes.*

class DocumentsRoutesSpec
    extends CatsEffectSuite
    with ScalaCheckEffectSuite
    with Http4sTestingRoutesDsl
    with FileContentContext
    with DocumentsStoreContext:

  given noShrink[T]: Shrink[T] = Shrink.shrinkAny

  // tests are slow, so run them less often
  override def scalaCheckTestParameters = super.scalaCheckTestParameters.withMinSuccessfulTests(3)

  test("Post document") {
    forAllF(metadataG) { metadata =>
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

        _ <- whenSending(request)
          .to(new DocumentsRoutes[IO, IO](store).routes)
//
          .thenItReturns(Status.Created, withJsonPayload = metadata)
        storedMetadata <- store.fetchMetadata(metadata.name)
        uploadedBytes <- bytesFrom[IO]("mountain-bike-liguria-ponent.jpg").compile.toList
        storedBytes <- OptionT(store.fetchBytes(metadata.name)).semiflatMap(_.compile.toList).value
        _ <- IO {
          assertEquals(storedMetadata, metadata.some)
          assertEquals(storedBytes.map(_.toList), uploadedBytes.some)
        }
      yield ()
    }
  }

  test("Get document metadata") {
    forAllF(documents[IO]) { document =>
      val request = GET(Uri.unsafeFromString(s"/documents/metadata?name=${document.metadata.name.value}"))
      for
        store <- makeDocumentsStore(state = document)

        _ <- whenSending(request)
          .to(new DocumentsRoutes[IO, IO](store).routes)
//
          .thenItReturns(Status.Ok, withJsonPayload = document.metadata)
      yield ()
    }
  }

  test("Get document bytes") {
    forAllF(documents[IO]) { document =>
      val request = GET(Uri.unsafeFromString(s"/documents?name=${document.metadata.name.value}"))
      for
        store <- makeDocumentsStore(state = document)

        _ <- whenSending(request)
          .to(new DocumentsRoutes[IO, IO](store).routes)
//
          .thenItReturns(Status.Ok, withPayload = document.bytes)
      yield ()
    }
  }

  test("Delete document") {
    forAllF(documents[IO]) { document =>
      val request = DELETE(Uri.unsafeFromString(s"/documents?name=${document.metadata.name.value}"))
      for
        store <- makeDocumentsStore(state = document)

        _ <- whenSending(request)
          .to(new DocumentsRoutes[IO, IO](store).routes)
//
          .thenItReturns(Status.NoContent)
        stored <- IO.both(
          store.fetchMetadata(document.metadata.name),
          OptionT(store.fetchBytes(document.metadata.name)).semiflatMap(_.compile.toList).value
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

        _ <- whenSending(request)
          .to(new DocumentsRoutes[IO, IO](store).routes)
//
          .thenItReturns(
            Status.BadRequest,
            withJsonPayload = Incorrect("bytes".missing)
          )
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

      _ <- whenSending(request)
        .to(new DocumentsRoutes[IO, IO](store).routes)
//
        .thenItReturns(
          Status.BadRequest,
          withJsonPayload = Incorrect("metadata".missing)
        )
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

      _ <- whenSending(request)
        .to(new DocumentsRoutes[IO, IO](store).routes)
//
        .thenItReturns(
          Status.BadRequest,
          withJsonPayload = Incorrect("Malformed message body: Invalid JSON".malformed)
        )
    yield ()
  }

  test("Delete unexistent document == not found") {
    forAllF(documents[IO]) { document =>
      val request = DELETE(Uri.unsafeFromString(s"/documents?name=${document.metadata.name.value}"))
      for
        store <- makeDocumentsStore()

        _ <- whenSending(request)
          .to(new DocumentsRoutes[IO, IO](store).routes)
//
          .thenItReturns(Status.NotFound)
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

    val result: EitherT[IO, NonEmptyChain[Incorrect.Detail], (Document.Metadata, fs2.Stream[IO, Byte])] =
      (multipart.metadata, multipart.bytes).parTupled

    result
      .map { _ => fail("expected left") }
      .leftMap { result => assertEquals(result.toList, ("metadata".missing |+| "bytes".missing).toList) }
      .value
  }

trait DocumentsStoreContext:
  def makeDocumentsStore(): IO[DocumentsStore[IO, IO]] = makeDocumentsStore(state = Map.empty)

  def makeDocumentsStore(state: Document[IO]): IO[DocumentsStore[IO, IO]] = makeDocumentsStore(Map(state.metadata.name -> state))

  private def makeDocumentsStore(state: Map[Document.Metadata.Name, Document[IO]]): IO[DocumentsStore[IO, IO]] =
    Ref.of[IO, Map[Document.Metadata.Name, Document[IO]]](state).map { storage =>
      new DocumentsStore[IO, IO]:
        def store(doc: Document[IO]): IO[Unit] = storage.update { state =>
          state.updated(doc.metadata.name, doc)
        }

        def fetchMetadata(name: Document.Metadata.Name): IO[Option[Document.Metadata]] = storage.get.map { state =>
          state.find(s => s._1 === name).map(_._2.metadata)
        }

        def fetchBytes(name: Document.Metadata.Name): IO[Option[fs2.Stream[IO, Byte]]] = storage.get.map { state =>
          state.find(s => s._1 === name).map(_._2.bytes)
        }

        def delete(name: Document.Metadata.Name): IO[Unit] = storage.update { state =>
          state.removed(name)
        }

        val commit: [A] => IO[A] => IO[A] = [A] => (action: IO[A]) => action

        val lift: [A] => IO[A] => IO[A] = [A] => (action: IO[A]) => action
    }
