package org.fiume.sketch.acceptance

import munit.CatsEffectSuite
import org.fiume.sketch.shared.testkit.Http4sClientContext

class BaselineAccSpec extends CatsEffectSuite with Http4sClientContext:

  test("ping returns pong"):
    withHttp { client =>
      client.expect[String]("http://localhost:8080/ping".get).map { res =>
        assertEquals(res, "\"pong\"") // json string
      }
    }
