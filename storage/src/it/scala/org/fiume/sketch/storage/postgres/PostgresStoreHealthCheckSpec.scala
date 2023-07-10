package org.fiume.sketch.storage.postgres

import cats.effect.IO
import munit.CatsEffectSuite
import org.fiume.sketch.shared.app.algebras.HealthCheck.ServiceHealth
import org.fiume.sketch.shared.app.algebras.HealthCheck.ServiceHealth.Infra
import org.fiume.sketch.storage.postgres.PostgresStore
import org.fiume.sketch.storage.test.support.DockerPostgresSuite
import org.scalacheck.Shrink

class PostgresStoreHealthCheckSpec extends CatsEffectSuite with DockerPostgresSuite:

  // shrinking just make failing tests messages more obscure
  given noShrink[T]: Shrink[T] = Shrink.shrinkAny

  test("db is healthy") {
    PostgresStore.make[IO](transactor()).use { store =>
      for
        result <- store.check
        _ <- IO { assertEquals(result, ServiceHealth.healthy(Infra.Database)) }
      yield ()
    }
  }
