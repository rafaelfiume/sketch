package org.fiume.sketch.datastore

import cats.data.{EitherT, NonEmptyChain, OptionT}
import cats.effect.{IO, Ref}
import cats.implicits.*
import io.circe.Json
import io.circe.syntax.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import munit.Assertions.*
import org.http4s.{MediaType, *}
import org.http4s.Method.*
import org.http4s.circe.*
import org.http4s.client.*
import org.http4s.client.dsl.io.*
import org.http4s.ember.client.*
import org.http4s.headers.`Content-Type`
import org.http4s.implicits.*
import org.http4s.multipart.{Boundary, Multipart, Part}

class StoreDocumentsSpec extends CatsEffectSuite with StoreDocumentsSpecContext:

  // should be moved to a separated suite in the future
  test("ping returns pong") {
    http { client =>
      client.expect[String](uri"http://localhost:8080/ping").map { res =>
        assertEquals(res, "\"pong\"") // json string
      }
    }
  }

  test("store documents") {
    val docName = "some-random-unique-name"
    val docDesc = "La bella Altamura in Puglia <3"
    val pathToFile = "altamura.jpg"
    http { client =>
      for
        uploadResponse <- client.expect[Json](fileUploadRequest(payload(docName, docDesc), pathToFile))
        _ <- IO {
          assertEquals(uploadResponse.docName, docName)
          assertEquals(uploadResponse.description, docDesc)
        }

        metadataResponse <- client.expect[Json](
          Uri.unsafeFromString(s"http://localhost:8080/documents/metadata?name=${uploadResponse.docName}")
        )
        _ <- IO {
          assertEquals(metadataResponse.docName, docName)
          assertEquals(metadataResponse.description, docDesc)
        }

        docBytes <- client
          .stream(GET(Uri.unsafeFromString(s"http://localhost:8080/documents?name=${uploadResponse.docName}")))
          .flatMap(_.body)
          .compile
          .toList
        originalBytes <- bytesFrom[IO](pathToFile).compile.toList
        _ <- IO { assertEquals(docBytes, originalBytes) }
      yield ()
    }
  }

trait StoreDocumentsSpecContext:
  def http(exec: Client[IO] => IO[Unit]): IO[Unit] =
    EmberClientBuilder.default[IO].build.use { exec(_) }

  def payload(name: String, description: String): String =
    s"""
       |{
       |  "name": "$name",
       |  "description": "$description"
       |}
      """.stripMargin

  def fileUploadRequest(payload: String, pathToFile: String): Request[IO] =
    val imageFile = getClass.getClassLoader.getResource(pathToFile)
    val multipart = Multipart[IO](
      parts = Vector(
        Part.formData("metadata", payload),
        Part.fileData("document", imageFile, `Content-Type`(MediaType.image.jpeg))
      ),
      boundary = Boundary("boundary")
    )
    POST(uri"http://localhost:8080/documents").withEntity(multipart).withHeaders(multipart.headers)

  extension (json: Json)
    def docName: String = json.hcursor.get[String]("name").getOrElse(fail("'name' field not found"))
    def description: String = json.hcursor.get[String]("description").getOrElse(fail("'description' field not found"))

  // TODO duplicated from FileContentContext
  import cats.effect.Async
  import fs2.io.file.{Files, Path}
  def bytesFrom[F[_]](path: String)(using F: Async[F]): fs2.Stream[F, Byte] =
    Files[F].readAll(Path(getClass.getClassLoader.getResource(path).getPath()))
