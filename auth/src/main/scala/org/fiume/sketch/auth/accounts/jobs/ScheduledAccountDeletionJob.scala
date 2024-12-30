package org.fiume.sketch.auth.accounts.jobs

import cats.Monad
import cats.effect.Sync
import cats.implicits.*
import org.fiume.sketch.shared.auth.UserId
import org.fiume.sketch.shared.auth.accounts.{AccountDeletionEvent, AccountDeletionEventConsumer}
import org.fiume.sketch.shared.auth.algebras.UsersStore
import org.fiume.sketch.shared.common.events.EventId
import org.fiume.sketch.shared.common.jobs.Job
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.syntax.LoggerInterpolator

object ScheduledAccountDeletionJob:
  def make[F[_]: Sync, Txn[_]: Monad](eventConsumer: AccountDeletionEventConsumer[Txn], store: UsersStore[F, Txn]) =
    new ScheduledAccountDeletionJob(eventConsumer, store)

private class ScheduledAccountDeletionJob[F[_]: Sync, Txn[_]: Monad] private (
  eventConsumer: AccountDeletionEventConsumer[Txn],
  store: UsersStore[F, Txn]
) extends Job[F, Option[(EventId, UserId)]]:

  given Logger[F] = Slf4jLogger.getLogger[F]

  override val description: String = "permanent deletion of a user account"

  override def run(): F[Option[(EventId, UserId)]] =
    store.commit {
      eventConsumer.consumeEvent().flatMap {
        case Some(scheduled) =>
          store.deleteAccount(scheduled.userId).map(_ => Some((scheduled.uuid, scheduled.userId))) <*
            store.lift { info"Job ${scheduled.uuid} deleted account with id: ${scheduled.userId}" }
        case None => none.pure[Txn]
      }
    }
