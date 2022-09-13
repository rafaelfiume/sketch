package org.fiume.sketch.datastore.http

import cats.effect.IO
import io.circe.{Encoder, Json}
import io.circe.syntax.*
import munit.{CatsEffectSuite, ScalaCheckSuite}
import org.fiume.sketch.datastore.http.DocumentsRoutes
import org.fiume.sketch.datastore.http.JsonCodecs.Documents.given
import org.fiume.sketch.domain.Document
import org.fiume.sketch.support.Http4sTestingRoutesDsl
import org.fiume.sketch.support.gens.SketchGens.Documents.*
import org.http4s.{MediaType, _}
import org.http4s.Method.*
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.client.dsl.io.*
import org.http4s.headers.`Content-Type`
import org.http4s.implicits.*
import org.http4s.multipart.{Boundary, Multipart, Part}
import org.scalacheck.effect.PropF.forAllF

class DocumentsRoutesSpec extends CatsEffectSuite with Http4sTestingRoutesDsl with DocumentsRoutesSpecContext:

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
    whenSending(request)
      .to(new DocumentsRoutes[IO].routes)
      .thenItReturns(Status.Created, withPayload = metadata)

  }

trait DocumentsRoutesSpecContext:

  given Encoder[Document.Metadata.Name] = Encoder.encodeString.contramap(_.value)
  given Encoder[Document.Metadata.Description] = Encoder.encodeString.contramap(_.value)

  given Encoder[Document.Metadata] = new Encoder[Document.Metadata]:
    override def apply(metadata: Document.Metadata): Json =
      Json.obj(
        "name" -> metadata.name.asJson,
        "description" -> metadata.description.asJson
      )
