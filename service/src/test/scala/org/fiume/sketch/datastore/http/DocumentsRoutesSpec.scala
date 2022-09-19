package org.fiume.sketch.datastore.http

import cats.data.{NonEmptyChain, OptionT}
import cats.effect.{IO, Ref}
import cats.implicits.*
import io.circe.syntax.*
import munit.CatsEffectSuite
import org.fiume.sketch.datastore.algebras.DocumentsStore
import org.fiume.sketch.datastore.http.DocumentsRoutes
import org.fiume.sketch.datastore.http.JsonCodecs.Documents.given
import org.fiume.sketch.datastore.http.JsonCodecs.Incorrects.given
import org.fiume.sketch.datastore.http.Model.Incorrect
import org.fiume.sketch.datastore.http.Model.IncorrectOps.*
import org.fiume.sketch.domain.Document
import org.fiume.sketch.support.{FileContentContext, Http4sTestingRoutesDsl}
import org.fiume.sketch.support.gens.SketchGens.Documents.*
import org.http4s.{MediaType, _}
import org.http4s.Method.*
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.client.dsl.io.*
import org.http4s.headers.`Content-Type`
import org.http4s.implicits.*
import org.http4s.multipart.{Boundary, Multipart, Part}

class DocumentsRoutesSpec extends CatsEffectSuite with Http4sTestingRoutesDsl with FileContentContext with DocumentsStoreContext:

  test("Post document") {
    val metadata = metadataG.sample.get
    val image = getClass.getClassLoader.getResource("mountain-bike-liguria-ponent.jpg")
    val multipart = Multipart[IO](
      parts = Vector(
        Part.formData("metadata", metadata.asJson.spaces2SortKeys),
        Part.fileData("document", image, `Content-Type`(MediaType.image.jpeg))
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

  test("Get document metadata") {
    val document = documents[IO].sample.get
    val request = GET(Uri.unsafeFromString(s"/documents/metadata?name=${document.metadata.name.value}"))
    for
      store <- makeDocumentsStore(state = document)

      _ <- whenSending(request)
        .to(new DocumentsRoutes[IO, IO](store).routes)
//
        .thenItReturns(Status.Ok, withJsonPayload = document.metadata)
    yield ()
  }

  test("Get document bytes") {
    val document = documents[IO].sample.get
    val request = GET(Uri.unsafeFromString(s"/documents?name=${document.metadata.name.value}"))
    for
      store <- makeDocumentsStore(state = document)

      _ <- whenSending(request)
        .to(new DocumentsRoutes[IO, IO](store).routes)
//
        .thenItReturns(Status.Ok, withPayload = document.bytes)
    yield ()
  }

  test("Delete document") {
    val document = documents[IO].sample.get
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

  /* Sad Path */

  test("Post document with no file == bad request") {
    val metadata = metadataG.sample.get
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

  test("Post document with no metadata == bad request") {
    val image = getClass.getClassLoader.getResource("mountain-bike-liguria-ponent.jpg")
    val multipart = Multipart[IO](
      // no metadata mamma!
      parts = Vector(Part.fileData("document", image, `Content-Type`(MediaType.image.jpeg))),
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
    val image = getClass.getClassLoader.getResource("mountain-bike-liguria-ponent.jpg")
    val multipart = Multipart[IO](
      parts = Vector(
        Part.formData("metadata", """ { \"bananas\" : \"apples\" } """),
        Part.fileData("document", image, `Content-Type`(MediaType.image.jpeg))
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
