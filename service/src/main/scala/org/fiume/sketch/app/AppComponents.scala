package org.fiume.sketch.app

import cats.effect.{Async, Clock, Resource, Sync}
import doobie.ConnectionIO
import fs2.io.net.Network
import org.fiume.sketch.app.SketchVersions.VersionFile
import org.fiume.sketch.auth.Authenticator
import org.fiume.sketch.auth.accounts.UsersManager
import org.fiume.sketch.rustic.RusticHealthCheck
import org.fiume.sketch.shared.auth.accounts.{
  AccountDeletedNotificationConsumer,
  AccountDeletedNotificationProducer,
  AccountDeletionEventConsumer
}
import org.fiume.sketch.shared.auth.algebras.UsersStore
import org.fiume.sketch.shared.authorisation.AccessControl
import org.fiume.sketch.shared.common.app.{HealthChecker, Versions}
import org.fiume.sketch.shared.common.app.ServiceStatus.Dependency.*
import org.fiume.sketch.shared.common.events.Recipient
import org.fiume.sketch.shared.domain.documents.algebras.DocumentsStore
import org.fiume.sketch.storage.auth.postgres.{
  PostgresAccountDeletedNotificationsStore,
  PostgresAccountDeletionEventsStore,
  PostgresUsersStore
}
import org.fiume.sketch.storage.authorisation.postgres.PostgresAccessControl
import org.fiume.sketch.storage.documents.postgres.PostgresDocumentsStore
import org.fiume.sketch.storage.postgres.{DbTransactor, PostgresHealthCheck}
import org.http4s.ember.client.EmberClientBuilder
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

import java.util.concurrent.{Executors, ThreadFactory}
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*

trait AppComponents[F[_]]:
  /**
   * App admin
   */
  val customWorkerThreadPool: ExecutionContext
  val dbHealthCheck: HealthChecker.DependencyHealthChecker[F, Database]
  val rusticHealthCheck: HealthChecker.DependencyHealthChecker[F, Rustic]
  val versions: Versions[F]

  /*
   * Authentication & Account Mangement
   */
  val authenticator: Authenticator[F]
  val accountDeletionEventConsumer: AccountDeletionEventConsumer[ConnectionIO]
  val accountDeletedNotificationProducer: AccountDeletedNotificationProducer[ConnectionIO]
  val usersStore: UsersStore[F, ConnectionIO]
  val usersManager: UsersManager[F]

  /**
   * Authorisation
   */
  val accessControl: AccessControl[F, ConnectionIO]

  /**
   * Domain
   */
  val documentsStore: DocumentsStore[F, ConnectionIO]
  val accountDeletedNotificationConsumer: AccountDeletedNotificationConsumer[ConnectionIO]

object AppComponents:
  given [F[_]: Sync] => LoggerFactory[F] = Slf4jFactory.create[F]

  def make[F[_]: { Async, Network }](config: AppConfig.Static): Resource[F, AppComponents[F]] =
    for
      customWorkerThreadPool0 <- newCustomWorkerThreadPool()
      transactor <- DbTransactor.make(config.db)
      dbHealthCheck0 <- PostgresHealthCheck.make[F](transactor)
      httpClient <- EmberClientBuilder.default[F].build
      rusticHealthCheck0 = RusticHealthCheck.make[F](config.rusticClient, httpClient)
      versions0 <- SketchVersions.make[F](config.env, VersionFile("sketch.version"))

      accountDeletionEventStore0 <- PostgresAccountDeletionEventsStore.make[F]()
      accountDeletedNotificationProducer0 <- PostgresAccountDeletedNotificationsStore.makeProducer[F]()
      usersStore0 <- PostgresUsersStore.make[F](transactor)
      authenticator0 <- Resource.liftK {
        Authenticator.make[F, ConnectionIO](
          Clock[F],
          usersStore0,
          config.keyPair.privateKey,
          config.keyPair.publicKey,
          expirationOffset = 1.hour
        )
      }
      accessControl0 <- PostgresAccessControl.make[F](transactor)
      usersManager0 = UsersManager.make[F, ConnectionIO](
        usersStore0,
        accountDeletionEventStore0,
        accessControl0,
        Clock[F],
        config.account.delayUntilPermanentDeletion
      )

      documentsStore0 <- PostgresDocumentsStore.make[F](transactor)
      accountDeletedNotificationConsumer0 <- PostgresAccountDeletedNotificationsStore.makeConsumer(
        Recipient("sketch") // see system.dynamic_configs table
      )
//
    yield new AppComponents[F]:
      override val customWorkerThreadPool: ExecutionContext = customWorkerThreadPool0
      override val dbHealthCheck: HealthChecker.DependencyHealthChecker[F, Database] = dbHealthCheck0
      override val rusticHealthCheck: HealthChecker.DependencyHealthChecker[F, Rustic] = rusticHealthCheck0
      override val versions: Versions[F] = versions0

      override val authenticator: Authenticator[F] = authenticator0
      override val accountDeletionEventConsumer: AccountDeletionEventConsumer[ConnectionIO] =
        accountDeletionEventStore0
      override val accountDeletedNotificationProducer: AccountDeletedNotificationProducer[ConnectionIO] =
        accountDeletedNotificationProducer0
      override val usersStore: UsersStore[F, ConnectionIO] = usersStore0
      override val usersManager: UsersManager[F] = usersManager0

      override val accessControl: AccessControl[F, ConnectionIO] = accessControl0

      override val documentsStore: DocumentsStore[F, ConnectionIO] = documentsStore0
      override val accountDeletedNotificationConsumer = accountDeletedNotificationConsumer0

  private def newCustomWorkerThreadPool[F[_]: Sync]() = Resource
    .make(
      Sync[F].delay {
        Executors.newCachedThreadPool(
          new ThreadFactory:
            private val counter = new AtomicLong(0L)
            def newThread(r: Runnable): Thread =
              val th = new Thread(r)
              th.setName("custom-worker-thread-pool-" + counter.getAndIncrement.toString)
              th.setDaemon(true)
              th
        )
      }
    )(tp => Sync[F].delay { tp.shutdown() })
    .map(ExecutionContext.fromExecutorService)
