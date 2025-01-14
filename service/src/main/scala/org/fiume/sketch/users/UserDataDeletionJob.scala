package org.fiume.sketch.users

import cats.Monad
import cats.effect.kernel.Sync
import cats.implicits.*
import org.fiume.sketch.shared.auth.UserId
import org.fiume.sketch.shared.auth.accounts.{AccountDeletedNotification, AccountDeletedNotificationConsumer}
import org.fiume.sketch.shared.common.EntityId
import org.fiume.sketch.shared.common.app.syntax.StoreSyntax.commitStream
import org.fiume.sketch.shared.common.events.EventId
import org.fiume.sketch.shared.common.jobs.Job
import org.fiume.sketch.shared.domain.documents.algebras.DocumentsStore
import org.fiume.sketch.users.UserDataDeletionJob.JobReport
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.syntax.LoggerInterpolator

object UserDataDeletionJob:
  case class JobReport(
    triggeringEventId: EventId,
    deletedUserId: UserId, // The user whose account has been deleted
    deletedEntities: List[EntityId[?]] // The id of the entities deleted as result of the user account deletions
  )

  def make[F[_]: Sync, Txn[_]: Monad](
    accountDeletedNotificationConsumer: AccountDeletedNotificationConsumer[Txn],
    store: DocumentsStore[F, Txn]
  ) =
    new UserDataDeletionJob[F, Txn](
      accountDeletedNotificationConsumer,
      store
    )

private class UserDataDeletionJob[F[_]: Sync, Txn[_]: Monad] private (
  accountDeletedNotificationConsumer: AccountDeletedNotificationConsumer[Txn],
  store: DocumentsStore[F, Txn]
) extends Job[F, Option[JobReport]]:

  // enable Store's syntax
  given DocumentsStore[F, Txn] = store

  /*
   * There appears to be a way to instantiate a Logger without requiring `Sync`,
   * but it seems a bit convoluted to me.
   * See https://typelevel.org/log4cats/#logging-using-capabilities
   */
  given Logger[F] = Slf4jLogger.getLogger[F]

  override val description: String = "Deletes user data following account deletion"

  override def run(): F[Option[JobReport]] =
    var notif0: Option[AccountDeletedNotification.Notified] = None
    val job = fs2.Stream
      .evals(accountDeletedNotificationConsumer.consumeEvent())
      .flatMap { notif =>
        notif0 = notif.some
        process(notif)
      }
    job
      .commitStream()
      .compile
      .toList
      .map { affectedEntityIds =>
        notif0.map(n => JobReport(n.uuid, n.userId, affectedEntityIds))
      }
      .flatTap { _.traverse_(r => info(r.triggeringEventId, r.deletedUserId, r.deletedEntities)) }

  private def process(event: AccountDeletedNotification.Notified): fs2.Stream[Txn, EntityId[?]] =
    store
      .fetchDocumentsByOwnerId(event.userId)
      .evalMap { doc => store.delete(doc.uuid) }
      .unNone
  /*
   * Note: Using `parEvalMapUnbounded`instead of `evalMap` would have been nice.
   * However, that would require an instance of `cats.effect.Concurrent[ConnectionIO]`, which isn't available.
   * See https://github.com/typelevel/doobie/issues/1561
   */

  private def info(eventId: EventId, userId: UserId, entityIds: List[EntityId[?]]): F[Unit] =
    val l = s"UserDataDeletionJob completed successfully: triggeringEventId=${eventId}, " +
      s"deletedUserId=${userId}, " +
      s"affectedEntityIds=${entityIds.map(id => s"[UUID: ${id}, Entity Type: ${id.entityType}]").mkString(", ")}}"
    info"$l"
