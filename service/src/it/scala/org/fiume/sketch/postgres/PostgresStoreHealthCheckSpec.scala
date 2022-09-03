package org.fiume.sketch.postgres

import cats.effect.*
import cats.effect.std.Random
import cats.syntax.all.*
import munit.ScalaCheckEffectSuite
import org.fiume.sketch.support.{ClockContext, DockerPostgresSuite}
import org.scalacheck.Gen.choose
import org.scalacheck.Shrink
import org.scalacheck.effect.PropF.forAllF

import java.time.{ZoneOffset, ZonedDateTime}
import scala.concurrent.duration.*

class PostgresStoreHealthCheckSpec extends DockerPostgresSuite with ScalaCheckEffectSuite with ClockContext:

  // shrinking just make failing tests messages more obscure
  given noShrink[T]: Shrink[T] = Shrink.shrinkAny

  override def scalaCheckTestParameters = super.scalaCheckTestParameters.withMinSuccessfulTests(5)

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
