package org.fiume.sketch.postgres

import cats.effect.*
import munit.ScalaCheckEffectSuite
import org.fiume.sketch.datastore.postgres.PostgresStore
import org.fiume.sketch.datastore.support.DockerPostgresSuite
import org.fiume.sketch.support.ClockContext
import org.scalacheck.Shrink

import java.time.{ZoneOffset, ZonedDateTime}

class PostgresStoreHealthCheckSpec extends DockerPostgresSuite with ScalaCheckEffectSuite with ClockContext:

  // shrinking just make failing tests messages more obscure
  given noShrink[T]: Shrink[T] = Shrink.shrinkAny

  private val frozen = ZonedDateTime.of(2021, 12, 3, 10, 11, 12, 0, ZoneOffset.UTC)

  test("healthcheck when db is healthy") {
    val clock = makeFrozenTime(frozen)
    PostgresStore.make[IO](clock, transactor()).use { store =>
      for
        result <- store.healthCheck
        _ <- IO { assertEquals(result, ()) }
      yield ()
    }
  }

  test("healthcheck when db is not healthy".ignore) {
    // TODO when we figure out how to interrupt the JDBC connection
  }
