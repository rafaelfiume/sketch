package org.fiume.sketch.postgres

import cats.effect.*
import munit.ScalaCheckEffectSuite
import org.fiume.sketch.datastore.postgres.PostgresStore
import org.fiume.sketch.datastore.support.DockerPostgresSuite
import org.scalacheck.Shrink

class PostgresStoreHealthCheckSpec extends DockerPostgresSuite with ScalaCheckEffectSuite:

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

  test("healthcheck when db is not healthy".ignore) {
    // TODO when we figure out how to interrupt the JDBC connection
  }
