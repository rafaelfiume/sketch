package org.fiume.sketch.app

import cats.effect.{Async, Resource}
import doobie.ConnectionIO
import doobie.hikari.HikariTransactor
import doobie.util.transactor.Transactor
import org.fiume.sketch.algebras.*
import org.fiume.sketch.app.ServiceConfig.DatabaseConfig
import org.fiume.sketch.datastore.algebras.{DocumentsStore, *}
import org.fiume.sketch.datastore.postgres.{PostgresStore, SchemaMigration}

import java.util.concurrent.{Executors, ThreadFactory}
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.ExecutionContext

trait Resources[F[_]]:
  val store: DocumentsStore[F, ConnectionIO] & HealthCheck[F]

object Resources:
  def make[F[_]](config: ServiceConfig)(using F: Async[F]): Resource[F, Resources[F]] =
    for
      transactor <- makeDoobieTransactor(config.db)
      store0 <- PostgresStore.make[F](transactor)
    yield new Resources[F]:
      override val store: DocumentsStore[F, ConnectionIO] & HealthCheck[F] = store0

  private def makeDoobieTransactor[F[_]: Async](config: DatabaseConfig): Resource[F, Transactor[F]] = for
    dbPool <-
      Resource
        .make(
          Async[F].delay(
            Executors.newFixedThreadPool(
              // Match the default size of the Hikari pool
              // Also https://github.com/brettwooldridge/HikariCP/wiki/About-Pool-Sizing
              10,
              new ThreadFactory:
                private val counter = new AtomicLong(0L)

                def newThread(r: Runnable): Thread =
                  val th = new Thread(r)
                  th.setName("db-thread-" + counter.getAndIncrement.toString)
                  th.setDaemon(true)
                  th
            )
          )
        )(tp => Async[F].delay(tp.shutdown()))
        .map(ExecutionContext.fromExecutorService)
    _ <- Resource.eval(SchemaMigration[F](config))
    transactor <- HikariTransactor.newHikariTransactor[F](
      config.driver,
      config.url,
      config.user,
      config.password.value,
      dbPool
    )
  yield transactor
