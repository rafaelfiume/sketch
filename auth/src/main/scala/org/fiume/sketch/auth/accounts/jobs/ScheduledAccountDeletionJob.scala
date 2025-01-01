package org.fiume.sketch.auth.accounts.jobs

import cats.Monad
import cats.effect.Sync
import cats.implicits.*
import org.fiume.sketch.auth.accounts.jobs.ScheduledAccountDeletionJob.JobReport
import org.fiume.sketch.shared.auth.UserId
import org.fiume.sketch.shared.auth.accounts.{AccountDeletedNotificationProducer, AccountDeletionEventConsumer, Service}
import org.fiume.sketch.shared.auth.accounts.AccountDeletedNotification.{Notified, ToNotify}
import org.fiume.sketch.shared.auth.algebras.UsersStore
import org.fiume.sketch.shared.common.events.EventId
import org.fiume.sketch.shared.common.jobs.Job
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.syntax.LoggerInterpolator

object ScheduledAccountDeletionJob:
  case class JobReport(
    triggeringEventId: EventId,
    deletedUserId: UserId,
    notificationsSent: List[Notified]
  )

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
) extends Job[F, Option[JobReport]]:

  given Logger[F] = Slf4jLogger.getLogger[F]

  override val description: String = "permanent deletion of a user account"

  override def run(): F[Option[JobReport]] =
    val job = accountDeletionEventConsumer.consumeEvent().flatMap {
      case Some(scheduled) =>
        for
          _ <- store.deleteAccount(scheduled.userId)
          notifs <- List(Service("sketch")).traverse { target => // TODO Service/ConsumerGroup hardcoded for now
            notificationProducer.produceEvent(ToNotify(scheduled.userId, target))
          }
          _ <- info(scheduled.uuid, scheduled.userId, notifs)
        yield JobReport(scheduled.uuid, scheduled.userId, notifs).some
      case None => none.pure[Txn]
    }
    store.commit { job }

  private def info(eventId: EventId, userId: UserId, notifs: List[Notified]): Txn[Unit] =
    // I should probably think seriously about structure logging...
    val l = s"Job completed successfully: triggeringEventId=${eventId}, " +
      s"deletedUserId=${userId}, " +
      s"notificationsSent=${notifs.map(n => s"[ID: ${n.uuid}, Target: ${n.target}]").mkString(", ")}}"
    store.lift { info"$l" }
