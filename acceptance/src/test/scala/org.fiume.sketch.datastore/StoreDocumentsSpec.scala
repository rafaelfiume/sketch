package org.fiume.sketch.datastore

import cats.effect.IO
import munit.Assertions.*
import munit.CatsEffectSuite
import org.http4s.client.*
import org.http4s.ember.client.*
import org.http4s.implicits.*

class StoreDocumentsSpec extends CatsEffectSuite:

  // should be moved to a separated suite in the future
  test("ping") {
    EmberClientBuilder.default[IO].build.use { client =>
      client.expect[String](uri"http://localhost:8080/ping").map { res =>
        assertEquals(res, "\"pong\"") // json string
      }
    }
  }

  test("store documents".ignore) {
    fail("coming soon")
  }
