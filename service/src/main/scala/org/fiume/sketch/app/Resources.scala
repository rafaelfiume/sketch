package org.fiume.sketch.app

import cats.effect.{Async, Resource, Sync}
import doobie.ConnectionIO
import fs2.io.net.Network
import org.fiume.sketch.app.SketchVersions.VersionFile
import org.fiume.sketch.auth0.Authenticator
import org.fiume.sketch.rustic.RusticHealthCheck
import org.fiume.sketch.shared.app.ServiceStatus.Dependency.*
import org.fiume.sketch.shared.app.algebras.{HealthChecker, Versions}
import org.fiume.sketch.shared.app.algebras.HealthChecker.*
import org.fiume.sketch.shared.domain.documents.algebras.DocumentsStore
import org.fiume.sketch.storage.auth0.postgres.PostgresUsersStore
import org.fiume.sketch.storage.documents.postgres.PostgresDocumentsStore
import org.fiume.sketch.storage.postgres.{DbTransactor, PostgresHealthCheck}

import java.util.concurrent.{Executors, ThreadFactory}
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*

trait Resources[F[_]]:
  val customWorkerThreadPool: ExecutionContext
  val dbHealthCheck: HealthChecker.DependencyHealthChecker[F, Database]
  val rusticHealthCheck: HealthChecker.DependencyHealthChecker[F, Rustic]
  val versions: Versions[F]
  val authenticator: Authenticator[F]
  val documentsStore: DocumentsStore[F, ConnectionIO]

object Resources:
  def make[F[_]: Async: Network](config: ServiceConfig): Resource[F, Resources[F]] =
    for
      customWorkerThreadPool0 <- newCustomWorkerThreadPool()
      transactor <- DbTransactor.make(config.db)
      dbHealthCheck0 <- PostgresHealthCheck.make[F](transactor)
      rusticHealthCheck0 <- RusticHealthCheck.make[F](config.rusticClient)
      versions0 <- SketchVersions.make[F](config.env, VersionFile("sketch.version"))
      usersStore0 <- PostgresUsersStore.make[F](transactor)
      authenticator0 <- Resource.liftK {
        Authenticator.make[F, ConnectionIO](usersStore0,
                                            config.keyPair.privateKey,
                                            config.keyPair.publicKey,
                                            expirationOffset = 1.hour
        )
      }
      documentsStore0 <- PostgresDocumentsStore.make[F](transactor)
    yield new Resources[F]:
      override val customWorkerThreadPool: ExecutionContext = customWorkerThreadPool0
      override val dbHealthCheck: HealthChecker.DependencyHealthChecker[F, Database] = dbHealthCheck0
      override val rusticHealthCheck: HealthChecker.DependencyHealthChecker[F, Rustic] = rusticHealthCheck0
      override val versions: Versions[F] = versions0
      override val authenticator: Authenticator[F] = authenticator0
      override val documentsStore: DocumentsStore[F, ConnectionIO] = documentsStore0

  private def newCustomWorkerThreadPool[F[_]: Sync]() = Resource
    .make(
      Sync[F].delay(
        Executors.newCachedThreadPool(
          new ThreadFactory:
            private val counter = new AtomicLong(0L)
            def newThread(r: Runnable): Thread =
              val th = new Thread(r)
              th.setName("custom-worker-thread-pool-" + counter.getAndIncrement.toString)
              th.setDaemon(true)
              th
        )
      )
    )(tp => Sync[F].delay(tp.shutdown()))
    .map(ExecutionContext.fromExecutorService)
