package org.fiume.sketch.storage.postgres

import cats.effect.{Async, Resource}
import doobie.hikari.HikariTransactor
import doobie.util.transactor.Transactor
import org.fiume.sketch.storage.Config.DatabaseConfig

import java.util.concurrent.{Executors, ThreadFactory}
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.ExecutionContext

object DbTransactor:
  def make[F[_]: Async](config: DatabaseConfig): Resource[F, Transactor[F]] = for
    dbPool <-
      Resource
        .make(
          Async[F].delay(
            Executors.newFixedThreadPool(
              config.dbPoolThreads,
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
      config.uri.renderString,
      config.user,
      config.password.value,
      dbPool
    )
  yield transactor
