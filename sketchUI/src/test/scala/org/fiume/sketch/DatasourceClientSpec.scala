package org.fiume.sketch

import munit.Assertions.*
import munit.FunSuite
import org.fiume.sketch.domain.Document
import org.fiume.sketch.domain.Document.Metadata.*

class DatasourceClientSpec extends FunSuite:

  // TODO delete bla.jpg and altamura.jpg
  /*
   * Disabled since it depends on a server running, e.g. via `$ ./start-local.sh`.
   *
   * Note this is an integration test and it should be on an `it` folder.
   */
  import scala.concurrent.ExecutionContext
  given ExecutionContext = scala.concurrent.ExecutionContext.global
  test("upload document") {
    // given
    val name = Name("altamura.jpg")
    val description = Description("La bella Altamura in Puglia <3")
    val metadata = Document.Metadata(name, description)
    // TODO Leaving filePath out of Document for now
    val filePath = "sketchUI/src/test/resources/altamura.jpg"

    // when
    DatasourceClient
      .make("http://localhost:8080")
      .storeDocument(metadata, filePath)

      // then
      .map { metadata =>
        assertEquals(metadata.name, name)
        assertEquals(metadata.description, description)
      }
  }
