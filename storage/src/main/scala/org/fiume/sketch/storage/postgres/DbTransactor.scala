package org.fiume.sketch.storage.postgres

import cats.effect.{Async, Resource}
import doobie.hikari.HikariTransactor
import doobie.util.log.{LogEvent, LogHandler}
import doobie.util.transactor.Transactor

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
    debugSql = false
    printSqlLogHandler = new LogHandler[F]:
      def run(logEvent: LogEvent): F[Unit] = Async[F].delay { if debugSql then println(logEvent.sql) }
    transactor <- HikariTransactor.newHikariTransactor[F](
      config.driver,
      config.jdbcUri.renderString,
      config.user,
      config.password.value,
      dbPool,
      Some(printSqlLogHandler)
    )
  yield transactor
