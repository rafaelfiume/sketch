package org.fiume.sketch.storage.postgres

import cats.effect.IO
import munit.CatsEffectSuite
import org.fiume.sketch.shared.common.app.ServiceStatus.{DependencyStatus, Status}
import org.fiume.sketch.shared.common.app.ServiceStatus.Dependency.*
import org.fiume.sketch.storage.testkit.DockerPostgresSuite
import org.scalacheck.ShrinkLowPriority

class PostgresHealthCheckSpec extends CatsEffectSuite with DockerPostgresSuite with ShrinkLowPriority:

  test("dependency status is Ok when database is available") {
    PostgresHealthCheck.make[IO](transactor()).use { healthCheck =>
      for result <- healthCheck.check()
      yield assertEquals(result, DependencyStatus(database, Status.Ok))
    }
  }

  test("dependency status is Degraded when database is not available") {
    PostgresHealthCheck.make[IO](transactor()).use { healthCheck =>
      for
        _ <- IO.delay { container().stop() }
        result <- healthCheck.check()
      yield assertEquals(result, DependencyStatus(database, Status.Degraded))
    }
  }
