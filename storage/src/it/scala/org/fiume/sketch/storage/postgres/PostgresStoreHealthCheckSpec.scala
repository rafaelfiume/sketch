package org.fiume.sketch.storage.postgres

import cats.effect.IO
import munit.CatsEffectSuite
import org.fiume.sketch.storage.postgres.PostgresStore
import org.fiume.sketch.storage.support.DockerPostgresSuite
import org.scalacheck.Shrink

class PostgresStoreHealthCheckSpec extends CatsEffectSuite with DockerPostgresSuite:

  // shrinking just make failing tests messages more obscure
  given noShrink[T]: Shrink[T] = Shrink.shrinkAny

  test("healthcheck when db is healthy") {
    PostgresStore.make[IO](transactor()).use { store =>
      for
        result <- store.healthCheck
        _ <- IO { assertEquals(result, ()) }
      yield ()
    }
  }
