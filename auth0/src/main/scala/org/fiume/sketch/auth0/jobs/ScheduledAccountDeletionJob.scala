package org.fiume.sketch.auth0.jobs

import cats.Monad
import cats.effect.Sync
import cats.implicits.*
import org.fiume.sketch.shared.auth0.algebras.UsersStore
import org.fiume.sketch.shared.auth0.domain.UserId
import org.fiume.sketch.shared.jobs.{Job, JobId}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.syntax.LoggerInterpolator

object ScheduledAccountDeletionJob:
  def make[F[_]: Sync, Txn[_]: Monad](store: UsersStore[F, Txn]) = new ScheduledAccountDeletionJob(store)

private class ScheduledAccountDeletionJob[F[_]: Sync, Txn[_]: Monad] private (store: UsersStore[F, Txn])
    extends Job[F, Option[(JobId, UserId)]]:

  given Logger[F] = Slf4jLogger.getLogger[F]

  override val description: String = "permanent deletion of a user account"

  override def run(): F[Option[(JobId, UserId)]] =
    val job = for
      job <- store.claimNextJob()
      result <- job match
        case Some(job) =>
          store.deleteAccount(job.userId).map(_ => Some((job.uuid, job.userId))) <*
            store.lift { info"Job ${job.uuid} deleted account with id: ${job.userId}" }
        case None => none.pure[Txn]
    yield result

    store.commit { job }
