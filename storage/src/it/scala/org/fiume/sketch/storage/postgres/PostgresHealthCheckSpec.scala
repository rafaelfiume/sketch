package org.fiume.sketch.storage.postgres

import cats.effect.IO
import munit.CatsEffectSuite
import org.fiume.sketch.shared.app.ServiceStatus.{DependencyStatus, Status}
import org.fiume.sketch.shared.app.ServiceStatus.Dependency.*
import org.fiume.sketch.storage.testkit.DockerPostgresSuite
import org.scalacheck.ShrinkLowPriority

class PostgresHealthCheckSpec extends CatsEffectSuite with DockerPostgresSuite with ShrinkLowPriority:

  test("db is healthy") {
    PostgresHealthCheck.make[IO](transactor()).use { store =>
      for
        result <- store.check()
        _ <- IO { assertEquals(result, DependencyStatus(database, Status.Ok)) }
      yield ()
    }
  }
