package org.fiume.sketch

import munit.Assertions.*
import munit.FunSuite
import org.fiume.sketch.domain.Document
import org.fiume.sketch.domain.Document.Metadata.*
import scala.concurrent.ExecutionContext

class DatasourceClientSpec extends FunSuite:

  given ExecutionContext = scala.concurrent.ExecutionContext.global

  /*
   * Disabled since it depends on a server running, e.g. via `$ ./start-local.sh`.
   *
   * Note this is an integration test and it should be on an `it` folder.
   *
   * Note 2: unfortunatelly test doesn't work due to `TypeError: fetch failed`,
   * which is probably caused by a JS headless script environment used in for tests.
   * and thus `fetch` API not being availed.
   *
   * I've tried to fix the test by defining `jsEnv := new org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv`.
   * It didn't work for several reasons, so I'm postponing a fix for this test.
   *
   * Maybe we should rely less on unit tests and more on acceptance or end-2-end tests to check the frontend?
   */

  test("upload document".ignore) {
    // given
    val name = Name("altamura.jpg")
    val description = Description("La bella Altamura in Puglia <3")
    val bytes = Array[Byte](1, 2, 3, 4, 5)

    val host = "http://localhost"
    val port = "8080" // backend port
    // when
    DatasourceClient
      .make(s"$host:$port")
      .storeDocument(Document.Metadata(name, description), bytes)

      // then
      .map { metadata =>
        assertEquals(metadata.name, name)
        assertEquals(metadata.description, description)
      }
  }
