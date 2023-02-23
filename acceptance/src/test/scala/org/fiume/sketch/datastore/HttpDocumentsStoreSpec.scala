package org.fiume.sketch.datastore // TODO Rename `datastore` -> `storage`

import cats.effect.IO
import io.circe.Json
import munit.CatsEffectSuite
import munit.Assertions.*
import org.fiume.sketch.support.Http4sContext
import org.http4s.Status.*
import org.http4s.circe.*

class HttpDocumentsStoreSpec extends CatsEffectSuite with Http4sContext with StoreDocumentsSpecContext:

  val docName = "a-unique-name-for-altamural.jpg"
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
  // TODO Load from service/src/test/resources/contract/datasources/http/document.metadata.json
  def payload(name: String, description: String): String =
    s"""
       |{
       |  "name": "$name",
       |  "description": "$description"
       |}
      """.stripMargin

  extension (json: Json)
    def docName: String = json.hcursor.get[String]("name").getOrElse(fail("'name' field not found"))
    def description: String = json.hcursor.get[String]("description").getOrElse(fail("'description' field not found"))

  // TODO duplicated from FileContentContext
  import cats.effect.Async
  import fs2.io.file.{Files, Path}
  def bytesFrom[F[_]](path: String)(using F: Async[F]): fs2.Stream[F, Byte] =
    Files[F].readAll(Path(getClass.getClassLoader.getResource(path).getPath()))
