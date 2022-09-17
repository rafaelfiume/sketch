package org.fiume.sketch.datastore.http

import cats.data.OptionT
import cats.effect.{IO, Ref}
import cats.implicits.*
import io.circe.{Encoder, Json}
import io.circe.parser.{decode, parse}
import io.circe.syntax.*
import munit.CatsEffectSuite
import org.fiume.sketch.datastore.algebras.DocumentsStore
import org.fiume.sketch.datastore.http.DocumentsRoutes
import org.fiume.sketch.datastore.http.JsonCodecs.Documents.given
import org.fiume.sketch.domain.Document
import org.fiume.sketch.support.{FileContentContext, Http4sTestingRoutesDsl}
import org.fiume.sketch.support.EitherSyntax.*
import org.fiume.sketch.support.gens.SketchGens.Documents.*
import org.http4s.{MediaType, _}
import org.http4s.Method.*
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.client.dsl.io.*
import org.http4s.headers.`Content-Type`
import org.http4s.implicits.*
import org.http4s.multipart.{Boundary, Multipart, Part}

import java.net.URL

class DocumentsRoutesSpec
    extends CatsEffectSuite
    with Http4sTestingRoutesDsl
    with FileContentContext
    with DocumentsStoreContext
    with DocumentsRoutesSpecContext:

  test("upload documents") {
    val metadata = metadataG.sample.get
    val image = getClass.getClassLoader.getResource("mountain-bike-liguria-ponent.jpg")
    val multipart = Multipart[IO](
      parts = Vector(
        Part.formData("metadata", metadata.asJson.spaces2SortKeys),
        Part.fileData("document", image, `Content-Type`(MediaType.image.jpeg))
      ),
      boundary = Boundary("boundary")
    )
    val request = POST(uri"/documents/upload").withEntity(multipart).withHeaders(multipart.headers)
    for
      store <- makeDocumentsStore()
      _ <- whenSending(request)
        .to(new DocumentsRoutes[IO, IO](store).routes)
        .thenItReturns(Status.Created, withJsonPayload = metadata)
      storedMetadata <- store.fetchMetadata(metadata.name)
      uploadedBytes <- bytesFrom[IO]("mountain-bike-liguria-ponent.jpg").compile.toList
      storedBytes <- OptionT(store.fetchBytes(metadata.name)).semiflatMap(_.compile.toList).value
      _ <- IO { assertEquals(storedMetadata, metadata.some) }
      _ <- IO { assertEquals(storedBytes.map(_.toList), uploadedBytes.some) }
    yield ()
  }

  test("get document metadata") {
    val document = documents[IO].sample.get
    val request = GET(Uri.unsafeFromString(s"/documents/metadata?name=${document.metadata.name.value}"))
    for
      store <- makeDocumentsStore(state = document)
      _ <- whenSending(request)
        .to(new DocumentsRoutes[IO, IO](store).routes)
        .thenItReturns(Status.Ok, withJsonPayload = document.metadata)
    yield ()
  }

  test("no document metadata") {
    val document = documents.sample.get
    val request = GET(Uri.unsafeFromString(s"/documents/metadata?name=${document.metadata.name.value}"))
    for
      store <- makeDocumentsStore()
      _ <- whenSending(request)
        .to(new DocumentsRoutes[IO, IO](store).routes)
        .thenItReturns(Status.NotFound)
    yield ()
  }

  test("get document bytes") {
    val document = documents[IO].sample.get
    val request = GET(Uri.unsafeFromString(s"/documents?name=${document.metadata.name.value}"))
    for
      store <- makeDocumentsStore(state = document)
      _ <- whenSending(request)
        .to(new DocumentsRoutes[IO, IO](store).routes)
        .thenItReturns(Status.Ok, withPayload = document.bytes)
    yield ()
  }

  test("no document bytes") {
    val document = documents[IO].sample.get
    val request = GET(Uri.unsafeFromString(s"/documents?name=${document.metadata.name.value}"))
    for
      store <- makeDocumentsStore()
      _ <- whenSending(request)
        .to(new DocumentsRoutes[IO, IO](store).routes)
        .thenItReturns(Status.NotFound)
    yield ()
  }

  test("decode . encode <-> document metadata payload") {
    jsonFrom[IO]("contract/datasources/http/document.metadata.json").use { raw =>
      IO {
        val original = parse(raw).rightValue
        val metadata = decode[Document.Metadata](original.noSpaces).rightValue
        val roundTrip = metadata.asJson
        assertEquals(roundTrip.spaces2SortKeys, original.spaces2SortKeys)
      }
    }
  }

trait DocumentsRoutesSpecContext extends FileContentContext:

  given Encoder[Document.Metadata.Name] = Encoder.encodeString.contramap(_.value)
  given Encoder[Document.Metadata.Description] = Encoder.encodeString.contramap(_.value)

  given Encoder[Document.Metadata] = new Encoder[Document.Metadata]:
    override def apply(metadata: Document.Metadata): Json =
      Json.obj(
        "name" -> metadata.name.asJson,
        "description" -> metadata.description.asJson
      )

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

        def delete(name: Document.Metadata.Name): IO[Unit] = ???

        val commit: [A] => IO[A] => IO[A] = [A] => (action: IO[A]) => action

        val lift: [A] => IO[A] => IO[A] = [A] => (action: IO[A]) => action
    }
