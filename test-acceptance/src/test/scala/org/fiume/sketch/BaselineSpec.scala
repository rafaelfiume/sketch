package org.fiume.sketch

import cats.effect.IO
import munit.Assertions.*
import munit.CatsEffectSuite
import org.fiume.sketch.support.Http4sContext

import scala.concurrent.duration.*

class BaselineSpec extends CatsEffectSuite with Http4sContext:

  test("ping returns pong") {
    IO.sleep(500.millisecond) *> http { client =>
      client.expect[String]("http://localhost:8080/ping".get).map { res =>
        assertEquals(res, "\"pong\"") // json string
      }
    }
  }
