package org.fiume.sketch.storage.postgres

import cats.effect.IO
import ciris.Secret
import munit.CatsEffectSuite
import org.fiume.sketch.shared.common.app.ServiceStatus.{DependencyStatus, Status}
import org.fiume.sketch.shared.common.app.ServiceStatus.Dependency.*
import org.fiume.sketch.storage.testkit.{
  DockerDatabaseConfig,
  DockerPostgresSuite
}
import org.scalacheck.ShrinkLowPriority
import org.typelevel.doobie.hikari.HikariTransactor

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext

class PostgresHealthCheckSpec extends CatsEffectSuite with DockerPostgresSuite with ShrinkLowPriority:

  test("dependency status is Ok when database is available"):
    PostgresHealthCheck.make[IO](transactor()).use { healthCheck =>
      for result <- healthCheck.check()
      yield assertEquals(result, DependencyStatus(database, Status.Ok))
    }

  test("dependency status is Degraded when database is not available"):
    val unavailableConfig = DatabaseConfig(
      driver = DockerDatabaseConfig.driver,
      host = "localhost",
      port = 1, // nothing listening here
      name = DockerDatabaseConfig.database,
      user = DockerDatabaseConfig.user,
      password = Secret[String](DockerDatabaseConfig.password),
      dbPoolThreads = 1
    )
    val connectionPool = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(1))
    HikariTransactor
      .newHikariTransactor[IO](
        DockerDatabaseConfig.driver,
        unavailableConfig.jdbcUri.renderString,
        DockerDatabaseConfig.user,
        DockerDatabaseConfig.password,
        connectionPool,
        None
      )
      // fail fast — don't wait 30s for HikariCP to give up
      .evalTap(tx => IO(tx.kernel.setConnectionTimeout(2000)))
      .use { tx =>
        PostgresHealthCheck.make[IO](tx).use { healthCheck =>
          healthCheck.check().map { result =>
            assertEquals(result, DependencyStatus(database, Status.Degraded))
          }
        }
      }
