package org.fiume.sketch.auth.accounts.jobs

import cats.Monad
import cats.effect.Sync
import cats.implicits.*
import org.fiume.sketch.auth.accounts.AccountDeletedNotifier
import org.fiume.sketch.shared.auth.UserId
import org.fiume.sketch.shared.auth.algebras.UsersStore
import org.fiume.sketch.shared.common.jobs.{Job, JobId}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.syntax.LoggerInterpolator

object ScheduledAccountDeletionJob:
  def make[F[_]: Sync, Txn[_]: Monad](store: UsersStore[F, Txn], notifier: AccountDeletedNotifier[F]) =
    new ScheduledAccountDeletionJob(store, notifier)

private class ScheduledAccountDeletionJob[F[_]: Sync, Txn[_]: Monad] private (
  store: UsersStore[F, Txn],
  notifier: AccountDeletedNotifier[F]
) extends Job[F, Option[(JobId, UserId)]]:

  given Logger[F] = Slf4jLogger.getLogger[F]

  override val description: String = "permanent deletion of a user account"

  override def run(): F[Option[(JobId, UserId)]] =
    val job = for
      job <- store.claimNextJob()
      result <- job match
        case Some(job) =>
          for
            result <- store.deleteAccount(job.userId).map(_ => Some((job.uuid, job.userId)))
            _ <- store.lift { notifier.notify(job.userId) } // TODO Produce one event per dataset
            _ <- store.lift { info"Job ${job.uuid} deleted account with id: ${job.userId}" }
          yield result
        case None => none.pure[Txn]
    yield result
    store.commit { job }
