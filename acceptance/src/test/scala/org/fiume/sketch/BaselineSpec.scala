package org.fiume.sketch

import munit.CatsEffectSuite
import munit.Assertions.*
import org.fiume.sketch.support.Http4sContext

class BaselineSpec extends CatsEffectSuite with Http4sContext:

  test("ping returns pong") {
    http { client =>
      client.expect[String]("http://localhost:8080/ping".get).map { res =>
        assertEquals(res, "\"pong\"") // json string
      }
    }
  }
