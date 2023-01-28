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
import org.http4s.Status.*
import org.http4s.circe.*
import org.http4s.client.*
import org.http4s.client.dsl.io.*
import org.http4s.ember.client.*
import org.http4s.headers.`Content-Type`
import org.http4s.implicits.*
import org.http4s.multipart.{Boundary, Multipart, Part}

class HttpDocumentsStoreSpec extends CatsEffectSuite with StoreDocumentsSpecContext:

  // should be moved to a separated suite in the future
  test("ping returns pong") {
    http { client =>
      client.expect[String]("http://localhost:8080/ping".get).map { res =>
        assertEquals(res, "\"pong\"") // json string
      }
    }
  }

  val docName = "some-random-unique-name"
  val docDesc = "La bella Altamura in Puglia <3"
  val pathToFile = "altamura.jpg"

  test("store documents") {
    http { client =>
      for
        upload <- client.expect[Json](fileUploadRequest(payload(docName, docDesc), pathToFile)).flatTap { res =>
          IO {
            assertEquals(res.docName, docName)
            assertEquals(res.description, docDesc)
          }
        }

        _ <- client.expect[Json](s"http://localhost:8080/documents/metadata?name=${upload.docName}".get).map { res =>
          assertEquals(res.docName, docName)
          assertEquals(res.description, docDesc)
        }

        docBytes <- client
          .stream(s"http://localhost:8080/documents?name=${upload.docName}".get)
          .flatMap(_.body)
          .compile
          .toList
        originalBytes <- bytesFrom[IO](pathToFile).compile.toList
        _ <- IO { assertEquals(docBytes, originalBytes) }
      yield ()
    }
  }

  test("delete documents") {
    http { client =>
      for
        upload <- client.expect[Json](fileUploadRequest(payload(docName, docDesc), pathToFile))

        _ <- client.status(s"http://localhost:8080/documents?name=${upload.docName}".delete).map { status =>
          assertEquals(status, NoContent)
        }

        _ <- client.status(s"http://localhost:8080/documents/metadata?name=${upload.docName}".get).map { status =>
          assertEquals(status, NotFound)
        }

        _ <- client.status(s"http://localhost:8080/documents?name=${upload.docName}".get).map { status =>
          assertEquals(status, NotFound)
        }
      yield ()
    }
  }

trait StoreDocumentsSpecContext:
  def http(exec: Client[IO] => IO[Unit]): IO[Unit] = EmberClientBuilder.default[IO].build.use { exec(_) }

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
    "http://localhost:8080/documents".post.withEntity(multipart).withHeaders(multipart.headers)

  extension (json: Json)
    def docName: String = json.hcursor.get[String]("name").getOrElse(fail("'name' field not found"))
    def description: String = json.hcursor.get[String]("description").getOrElse(fail("'description' field not found"))

  // TODO duplicated from FileContentContext
  import cats.effect.Async
  import fs2.io.file.{Files, Path}
  def bytesFrom[F[_]](path: String)(using F: Async[F]): fs2.Stream[F, Byte] =
    Files[F].readAll(Path(getClass.getClassLoader.getResource(path).getPath()))

  extension (s: String)
    def get: Request[IO] = GET(s.toUri)
    def post: Request[IO] = POST(s.toUri)
    def delete: Request[IO] = DELETE(s.toUri)
    private def toUri: Uri = Uri.unsafeFromString(s)
