package org.fiume.sketch.auth.accounts.jobs

import cats.Monad
import cats.effect.Sync
import cats.implicits.*
import org.fiume.sketch.shared.auth.UserId
import org.fiume.sketch.shared.auth.accounts.{AccountDeletedNotificationProducer, AccountDeletionEventConsumer, Service}
import org.fiume.sketch.shared.auth.accounts.AccountDeletedNotification.ToNotify
import org.fiume.sketch.shared.auth.algebras.UsersStore
import org.fiume.sketch.shared.common.events.EventId
import org.fiume.sketch.shared.common.jobs.Job
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.syntax.LoggerInterpolator

object ScheduledAccountDeletionJob:
  def make[F[_]: Sync, Txn[_]: Monad](
    accountDeletionEventConsumer: AccountDeletionEventConsumer[Txn],
    notificationProducer: AccountDeletedNotificationProducer[Txn],
    store: UsersStore[F, Txn]
  ) =
    new ScheduledAccountDeletionJob(accountDeletionEventConsumer, notificationProducer, store)

private class ScheduledAccountDeletionJob[F[_]: Sync, Txn[_]: Monad] private (
  accountDeletionEventConsumer: AccountDeletionEventConsumer[Txn],
  notificationProducer: AccountDeletedNotificationProducer[Txn],
  store: UsersStore[F, Txn]
) extends Job[F, Option[(EventId, UserId)]]:

  given Logger[F] = Slf4jLogger.getLogger[F]

  override val description: String = "permanent deletion of a user account"

  override def run(): F[Option[(EventId, UserId)]] =
    store.commit {
      accountDeletionEventConsumer.consumeEvent().flatMap {
        case Some(scheduled) =>
          for
            _ <- store.deleteAccount(scheduled.userId)
            // TODO Service/ConsumerGroup hardcoded for now
            _ <- notificationProducer
              .produceEvent(ToNotify(scheduled.userId, Service("sketch"))) // TODO What about the return type?
            _ <- store.lift { info"Job ${scheduled.uuid} deleted account with id: ${scheduled.userId}" }
          yield (scheduled.uuid, scheduled.userId).some
        case None => none.pure[Txn]
      }
    }
