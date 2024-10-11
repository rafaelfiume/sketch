package org.fiume.sketch.storage.testkit

import cats.effect.{IO, Resource}
import ciris.Secret
import com.dimafeng.testcontainers.PostgreSQLContainer
import doobie.{ConnectionIO, Transactor}
import doobie.hikari.HikariTransactor
import doobie.implicits.*
import doobie.util.log.{LogEvent, LogHandler}
import munit.CatsEffectSuite
import org.fiume.sketch.storage.postgres.{DatabaseConfig, SchemaMigration}
import org.testcontainers.containers.PostgreSQLContainer as JavaPostgreSQLContainer
import org.testcontainers.utility.DockerImageName

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext

trait DockerPostgresSuite extends CatsEffectSuite:

  case class DbContainerAndTransactor(container: PostgreSQLContainer, transactor: Transactor[IO])

  override def munitFixtures = List(dbContainerAndTransactor)

  extension [A](ops: ConnectionIO[A]) def ccommit: IO[A] = ops.transact(dbContainerAndTransactor().transactor)

  extension [A](stream: fs2.Stream[ConnectionIO, A])
    def ccommitStream: fs2.Stream[IO, A] = stream.transact(dbContainerAndTransactor().transactor)

  /*
   * Mostly used to clean tables after running test.
   */
  def will[A](dbOps: ConnectionIO[Unit])(program: IO[A]): IO[A] =
    program.guarantee(dbOps.transact(dbContainerAndTransactor().transactor))

  def container(): PostgreSQLContainer = dbContainerAndTransactor().container

  def transactor(): Transactor[IO] = dbContainerAndTransactor().transactor

  private val dbContainerAndTransactor = ResourceSuiteLocalFixture(
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
      connectionPool: ExecutionContext = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(4))
      dbConfig = DatabaseConfig(
        driver = DockerDatabaseConfig.driver,
        host = container.host,
        port = container.mappedPort(JavaPostgreSQLContainer.POSTGRESQL_PORT),
        name = DockerDatabaseConfig.database,
        user = DockerDatabaseConfig.user,
        password = Secret[String](DockerDatabaseConfig.password),
        dbPoolThreads = 10 // ?
      )
      debugSql = false // set to 'true' to log SQL queries
      // See also: https://typelevel.org/doobie/docs/10-Logging.html
      printSqlLogHandler = new LogHandler[IO]:
        def run(logEvent: LogEvent): IO[Unit] = IO.delay { if debugSql then println(logEvent.sql) }
      tx <- HikariTransactor.newHikariTransactor[IO](
        DockerDatabaseConfig.driver,
        dbConfig.jdbcUri.renderString,
        DockerDatabaseConfig.user,
        DockerDatabaseConfig.password,
        connectionPool,
        Some(printSqlLogHandler)
      )
      _ <- SchemaMigration[IO](dbConfig).toResource
    yield DbContainerAndTransactor(container, tx)
  )
