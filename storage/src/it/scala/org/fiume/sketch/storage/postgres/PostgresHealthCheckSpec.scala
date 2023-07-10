package org.fiume.sketch.storage.postgres

import cats.effect.IO
import munit.CatsEffectSuite
import org.fiume.sketch.shared.app.algebras.HealthCheck.ServiceHealth
import org.fiume.sketch.shared.app.algebras.HealthCheck.ServiceHealth.Infra
import org.fiume.sketch.storage.documents.postgres.PostgresDocumentsStore
import org.fiume.sketch.storage.test.support.DockerPostgresSuite
import org.scalacheck.ShrinkLowPriority

class PostgresHealthCheckSpec extends CatsEffectSuite with DockerPostgresSuite with ShrinkLowPriority:

  test("db is healthy") {
    PostgresHealthCheck.make[IO](transactor()).use { store =>
      for
        result <- store.check
        _ <- IO { assertEquals(result, ServiceHealth.healthy(Infra.Database)) }
      yield ()
    }
  }
