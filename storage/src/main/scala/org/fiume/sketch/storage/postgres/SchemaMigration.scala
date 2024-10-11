package org.fiume.sketch.storage.postgres

import cats.effect.Sync
import cats.implicits.*
import org.flywaydb.core.Flyway
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.syntax.LoggerInterpolator

import scala.util.control.NonFatal

object SchemaMigration:
  def apply[F[_]: Sync](dbConfig: DatabaseConfig): F[Unit] =
    given Logger[F] = Slf4jLogger.getLogger[F]
    Sync[F]
      .delay {
        val flyway = Flyway.configure().dataSource(dbConfig.jdbcUri.renderString, dbConfig.user, dbConfig.password.value)
        flyway.load().migrate()
      }
      .flatMap { migrationsApplied =>
        info"Successfully applied $migrationsApplied migrations to the database"
      }
      .void
      .onError { case NonFatal(t) =>
        error"Unable to run Flyway migration with error: ${t.getMessage}"
      }
