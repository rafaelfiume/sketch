package org.fiume.sketch.auth.accounts.jobs

import cats.Monad
import cats.effect.Sync
import cats.implicits.*
import org.fiume.sketch.shared.auth.UserId
import org.fiume.sketch.shared.auth.accounts.jobs.ScheduledAccountDeletion
import org.fiume.sketch.shared.auth.algebras.UsersStore
import org.fiume.sketch.shared.common.jobs.{EventConsumer, Job, JobId}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.syntax.LoggerInterpolator

object ScheduledAccountDeletionJob:
  def make[F[_]: Sync, Txn[_]: Monad](eventConsumer: EventConsumer[Txn, ScheduledAccountDeletion], store: UsersStore[F, Txn]) =
    new ScheduledAccountDeletionJob(eventConsumer, store)

private class ScheduledAccountDeletionJob[F[_]: Sync, Txn[_]: Monad] private (
  eventConsumer: EventConsumer[Txn, ScheduledAccountDeletion],
  store: UsersStore[F, Txn]
) extends Job[F, Option[(JobId, UserId)]]:

  given Logger[F] = Slf4jLogger.getLogger[F]

  override val description: String = "permanent deletion of a user account"

  override def run(): F[Option[(JobId, UserId)]] =
    val job = for
      job <- eventConsumer.claimNextJob()
      result <- job match
        case Some(job) =>
          store.deleteAccount(job.userId).map(_ => Some((job.uuid, job.userId))) <*
            store.lift { info"Job ${job.uuid} deleted account with id: ${job.userId}" }
        case None => none.pure[Txn]
    yield result

    store.commit { job }
