package org.fiume.sketch.app

import cats.effect.{Async, Resource}
import doobie.ConnectionIO
import doobie.util.transactor.Transactor
import org.fiume.sketch.app.SketchVersions.VersionFile
import org.fiume.sketch.shared.app.algebras.{HealthCheck, Versions}
import org.fiume.sketch.storage.documents.algebras.DocumentsStore
import org.fiume.sketch.storage.documents.postgres.PostgresDocumentsStore
import org.fiume.sketch.storage.postgres.{DbTransactor, PostgresHealthCheck}

import java.util.concurrent.{Executors, ThreadFactory}
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.ExecutionContext

trait Resources[F[_]]:
  val customWorkerThreadPool: ExecutionContext
  val healthCheck: HealthCheck[F]
  val versions: Versions[F]
  val documentsStore: DocumentsStore[F, ConnectionIO]

object Resources:
  def make[F[_]](config: ServiceConfig)(using F: Async[F]): Resource[F, Resources[F]] =
    for
      customWorkerThreadPool0 <- newCustomWorkerThreadPool()
      transactor <- DbTransactor.make(config.db)
      healthCheck0 <- PostgresHealthCheck.make[F](transactor)
      versions0 <- SketchVersions.make[F](config.env, VersionFile("sketch.version"))
      documentsStore0 <- PostgresDocumentsStore.make[F](transactor)
    yield new Resources[F]:
      override val customWorkerThreadPool: ExecutionContext = customWorkerThreadPool0
      override val healthCheck: HealthCheck[F] = healthCheck0
      override val versions: Versions[F] = versions0
      override val documentsStore: DocumentsStore[F, ConnectionIO] = documentsStore0

  private def newCustomWorkerThreadPool[F[_]](using F: Async[F])() = Resource
    .make(
      F.delay(
        Executors.newCachedThreadPool(
          new ThreadFactory:
            private val counter = new AtomicLong(0L)
            def newThread(r: Runnable): Thread =
              val th = new Thread(r)
              th.setName("custome-worker-thread-pool-" + counter.getAndIncrement.toString)
              th.setDaemon(true)
              th
        )
      )
    )(tp => F.delay(tp.shutdown()))
    .map(ExecutionContext.fromExecutorService)
