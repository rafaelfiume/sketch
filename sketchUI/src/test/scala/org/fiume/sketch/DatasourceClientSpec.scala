package org.fiume.sketch

import cats.effect.IO
import munit.Assertions.*
import munit.CatsEffectSuite

class DatasourceClientSpec extends CatsEffectSuite with DatasourceClientSpecContext:

  /*
   * Disabled since it depends on a server running, e.g. via `$ ./start-local.sh`.
   *
   * Note this is an integration test and it should be on an `it` folder.
   */
  test("upload document".ignore) {
    val docName = "altamura-spec.jpg"
    val docDesc = "La bella Altamura in Puglia <3"
    val payload =
      s"""
         |{
         |"name": "$docName",
         |"description": "$docDesc"
         |}
    """.stripMargin

    for
      metadata <- DatasourceClient
        .make("http://localhost:8080")
        .storeDocument(payload, "sketchUI/src/test/resources/altamura.jpg")

      _ <- IO { println(metadata) }
      _ <- IO {
        assertEquals(metadata.docName, "banana")
        assertEquals(metadata.description, docDesc)
      }
    yield ()
  }

trait DatasourceClientSpecContext:
  import io.circe.Json

  // TODO duplicated from StoreDocumentsSpecContext
  extension (json: Json)
    def docName: String = json.hcursor.get[String]("name").getOrElse(fail("'name' field not found"))
    def description: String = json.hcursor.get[String]("description").getOrElse(fail("'description' field not found"))
