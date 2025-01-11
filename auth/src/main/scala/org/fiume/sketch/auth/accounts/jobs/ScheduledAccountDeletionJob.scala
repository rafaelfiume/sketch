package org.fiume.sketch.auth.accounts.jobs

import cats.Monad
import cats.effect.Sync
import cats.implicits.*
import org.fiume.sketch.auth.accounts.jobs.ScheduledAccountDeletionJob.JobReport
import org.fiume.sketch.auth.config.Dynamic.RecipientsKey
import org.fiume.sketch.auth.config.Dynamic.given
import org.fiume.sketch.shared.auth.UserId
import org.fiume.sketch.shared.auth.accounts.{AccountDeletedNotificationProducer, AccountDeletionEventConsumer}
import org.fiume.sketch.shared.auth.accounts.AccountDeletedNotification.{Notified, ToNotify}
import org.fiume.sketch.shared.auth.accounts.AccountDeletionEvent.Scheduled
import org.fiume.sketch.shared.auth.algebras.UsersStore
import org.fiume.sketch.shared.common.config.DynamicConfig
import org.fiume.sketch.shared.common.events.{EventId, Recipient}
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
    store: UsersStore[F, Txn],
    dynamicConfig: DynamicConfig[Txn]
  ) =
    new ScheduledAccountDeletionJob(accountDeletionEventConsumer, notificationProducer, store, dynamicConfig)

private class ScheduledAccountDeletionJob[F[_]: Sync, Txn[_]: Monad] private (
  accountDeletionEventConsumer: AccountDeletionEventConsumer[Txn],
  notificationProducer: AccountDeletedNotificationProducer[Txn],
  store: UsersStore[F, Txn],
  dynamicConfig: DynamicConfig[Txn]
) extends Job[F, Option[JobReport]]:

  given Logger[F] = Slf4jLogger.getLogger[F]

  override val description: String = "Permanently deletes a user account"

  override def run(): F[Option[JobReport]] =
    val job = accountDeletionEventConsumer
      .consumeEvent()
      .flatMap {
        _.fold(ifEmpty = none.pure[Txn])(process(_))
      }
    store.commit { job }

  private def process(scheduled: Scheduled): Txn[Option[JobReport]] =
    dynamicConfig.getConfig(RecipientsKey).flatMap {
      case None => none.pure[Txn] // TODO log
      case Some(recipients) =>
        for
          _ <- store.deleteAccount(scheduled.userId)
          notifs <- recipients.toList.traverse { recipient =>
            notificationProducer.produceEvent(ToNotify(scheduled.userId, recipient))
          }
          _ <- info(scheduled.uuid, scheduled.userId, notifs)
        yield JobReport(scheduled.uuid, scheduled.userId, notifs).some
    }

  private def info(eventId: EventId, userId: UserId, notifs: List[Notified]): Txn[Unit] =
    // I should probably think seriously about structure logging...
    val l = s"Job completed successfully: triggeringEventId=${eventId}, " +
      s"deletedUserId=${userId}, " +
      s"notificationsSent=${notifs.map(n => s"[ID: ${n.uuid}, Recipient: ${n.recipient}]").mkString(", ")}}"
    store.lift { info"$l" }
