package org.fiume.sketch.storage

import cats.effect.IO
import io.circe.Json
import munit.Assertions.*
import munit.CatsEffectSuite
import org.fiume.sketch.shared.test.FileContentContext
import org.fiume.sketch.support.Http4sClientContext
import org.http4s.Status.*
import org.http4s.circe.*

import scala.concurrent.duration.*

class HttpDocumentsStoreSpec
    extends CatsEffectSuite
    with Http4sClientContext
    with FileContentContext
    with StoreDocumentsSpecContext:

  val docName = "a-unique-name-for-altamural.jpg"
  val docDesc = "La bella Altamura in Puglia <3"
  val pathToFile = "altamura.jpg"

  test("store documents") {
    http { client =>
      for
        uuid <- IO.sleep(500.milliseconds) *>
          client.expect[Json](fileUploadRequest(payload(docName, docDesc), pathToFile)).map(_.uuid)

        _ <- client.expect[Json](s"http://localhost:8080/documents/$uuid/metadata".get).map { res =>
          assertEquals(res.docName, docName)
          assertEquals(res.description, docDesc)
        }

        content <- client
          .stream(s"http://localhost:8080/documents/$uuid/content".get)
          .flatMap(_.body)
          .compile
          .toList
        originalContent <- bytesFrom[IO](pathToFile).compile.toList
        _ <- IO { assertEquals(content, originalContent) }
      yield ()
    }
  }

  test("delete documents") {
    http { client =>
      for
        uuid <- client.expect[Json](fileUploadRequest(payload(docName, docDesc), pathToFile)).map(_.uuid)

        _ <- client.status(s"http://localhost:8080/documents/$uuid".delete).map { status =>
          assertEquals(status, NoContent)
        }

        _ <- client.status(s"http://localhost:8080/documents/$uuid/metadata".get).map { status =>
          assertEquals(status, NotFound)
        }

        _ <- client.status(s"http://localhost:8080/documents/$uuid/content".get).map { status =>
          assertEquals(status, NotFound)
        }
      yield ()
    }
  }

trait StoreDocumentsSpecContext:
  // TODO Load from storage/src/test/resources/storage/contract/http/document.metadata.json
  def payload(name: String, description: String): String =
    s"""
       |{
       |  "name": "$name",
       |  "description": "$description"
       |}
      """.stripMargin

  extension (json: Json)
    def uuid: String = json.hcursor.get[String]("uuid").getOrElse(fail("'uuid' field not found"))
    def docName: String = json.hcursor.get[String]("name").getOrElse(fail("'name' field not found"))
    def description: String = json.hcursor.get[String]("description").getOrElse(fail("'description' field not found"))
