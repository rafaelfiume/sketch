package org.fiume.sketch.auth0.jobs

import cats.Monad
import cats.effect.Sync
import cats.implicits.*
import org.fiume.sketch.shared.auth0.algebras.UsersStore
import org.fiume.sketch.shared.auth0.domain.UserId
import org.fiume.sketch.shared.jobs.{Job, JobId}
import org.typelevel.log4cats.slf4j.Slf4jLogger

object ScheduledAccountDeletionJob:
  def make[F[_]: Sync, Txn[_]: Monad](store: UsersStore[F, Txn]) = new ScheduledAccountDeletionJob(store)

private class ScheduledAccountDeletionJob[F[_]: Sync, Txn[_]: Monad] private (store: UsersStore[F, Txn])
    extends Job[F, Option[(JobId, UserId)]]:

  private val logger = Slf4jLogger.getLogger[F]

  override val description: String = "permanent deletion of an account"

  override def run(): F[Option[(JobId, UserId)]] =
    val job = for
      job <- store.claimNextJob()
      result <- job match
        case Some(job) =>
          store.delete(job.userId).map(_ => Some((job.uuid, job.userId))) <*
            store.lift { logger.info(s"Job ${job.uuid} deleted account with id: ${job.userId}") }
        case None => none.pure[Txn]
    yield result

    store.commit { job }
