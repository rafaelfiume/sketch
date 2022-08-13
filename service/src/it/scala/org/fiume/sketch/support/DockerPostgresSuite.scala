package org.fiume.sketch.support

import cats.effect.{IO, Resource}
import ciris.Secret
import com.dimafeng.testcontainers.PostgreSQLContainer
import doobie.Transactor
import doobie.hikari.HikariTransactor
import munit.CatsEffectSuite
import org.fiume.sketch.app.ServiceConfig.DatabaseConfig
import org.fiume.sketch.postgres.SchemaMigration
import org.testcontainers.containers.PostgreSQLContainer as JavaPostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext

trait DockerPostgresSuite extends CatsEffectSuite:

  override def munitFixtures = List(transactor)

  val transactor: Fixture[Transactor[IO]] = ResourceSuiteLocalFixture(
    "db-session",
    for
      container <- Resource
        .make(
          IO {
            val containerDef: PostgreSQLContainer.Def =
              PostgreSQLContainer.Def(
                dockerImageName = DockerImageName.parse(s"postgres:${DockerDatabaseConfig.postgreSQLVersion}"),
                databaseName = DockerDatabaseConfig.database,
                username = DockerDatabaseConfig.user,
                password = DockerDatabaseConfig.password
              )
            containerDef.start()
          }
        )(c => IO(c.stop()))
      connectionPool: ExecutionContext =
        ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(4))
      jdbcUrl = s"jdbc:postgresql://${container.host}:${container.mappedPort(JavaPostgreSQLContainer.POSTGRESQL_PORT)}/${DockerDatabaseConfig.database}"
      tx <- HikariTransactor.newHikariTransactor[IO](
        DockerDatabaseConfig.driver,
        jdbcUrl,
        DockerDatabaseConfig.user,
        DockerDatabaseConfig.password,
        connectionPool
      )
      dbConfig = DatabaseConfig(
        DockerDatabaseConfig.driver,
        jdbcUrl,
        DockerDatabaseConfig.user,
        Secret[String](DockerDatabaseConfig.password)
      )
      _ <- Resource.eval(SchemaMigration[IO](dbConfig))
    yield tx
  )
