package org.fiume.sketch.storage.testkit

import cats.effect.{IO, Resource}
import ciris.Secret
import com.dimafeng.testcontainers.PostgreSQLContainer
import doobie.{ConnectionIO, Transactor}
import doobie.hikari.HikariTransactor
import doobie.implicits.*
import munit.CatsEffectSuite
import org.fiume.sketch.storage.Config.DatabaseConfig
import org.fiume.sketch.storage.postgres.SchemaMigration
import org.http4s.Uri
import org.testcontainers.containers.PostgreSQLContainer as JavaPostgreSQLContainer
import org.testcontainers.utility.DockerImageName

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext

trait DockerPostgresSuite extends CatsEffectSuite:

  override def munitFixtures = List(transactor)

  extension [A](ops: ConnectionIO[A]) def ccommit: IO[A] = ops.transact(transactor())

  /*
   * Mostly used to clean tables after running test.
   */
  def will[A](dbOps: ConnectionIO[Unit])(program: IO[A]): IO[A] =
    program.guarantee(dbOps.transact(transactor()))

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
      jdbcUrl = Uri.unsafeFromString(
        s"jdbc:postgresql://${container.host}:${container.mappedPort(JavaPostgreSQLContainer.POSTGRESQL_PORT)}/${DockerDatabaseConfig.database}"
      )
      tx <- HikariTransactor.newHikariTransactor[IO](
        DockerDatabaseConfig.driver,
        jdbcUrl.renderString,
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
