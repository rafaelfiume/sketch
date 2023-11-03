package org.fiume.sketch.storage.postgres

import cats.effect.Sync
import cats.implicits.*
import org.fiume.sketch.storage.DatabaseConfig
import org.flywaydb.core.Flyway
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.util.control.NonFatal

object SchemaMigration:
  def apply[F[_]: Sync](dbConfig: DatabaseConfig): F[Unit] =
    val logger = Slf4jLogger.getLogger[F]
    Sync[F]
      .delay {
        val flyway = Flyway.configure().dataSource(dbConfig.jdbcUri.renderString, dbConfig.user, dbConfig.password.value)
        flyway.load().migrate()
      }
      .flatMap { migrationsApplied =>
        logger.info(s"Successfully applied $migrationsApplied migrations to the database")
      }
      .void
      .onError { case NonFatal(t) =>
        logger.error(s"Unable to run Flyway migration with error: ${t.getMessage}")
      }
