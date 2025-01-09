package org.fiume.sketch.storage.auth.postgres

import cats.effect.{Resource, Sync}
import doobie.*
import doobie.free.connection.ConnectionIO
import doobie.implicits.*
import org.fiume.sketch.shared.auth.accounts.{
  AccountDeletedNotification,
  AccountDeletedNotificationConsumer,
  AccountDeletedNotificationProducer
}
import org.fiume.sketch.shared.auth.accounts.AccountDeletedNotification.{Notified, ToNotify}
import org.fiume.sketch.shared.common.events.Recipient
import org.fiume.sketch.storage.auth.postgres.DatabaseCodecs.given

object PostgresAccountDeletedNotificationsStore:
  def makeProducer[F[_]: Sync](): Resource[F, AccountDeletedNotificationProducer[ConnectionIO]] =
    Resource.pure(new PostgresAccountDeletedNotificationProducerStore())

  def makeConsumer[F[_]: Sync](recipient: Recipient): Resource[F, AccountDeletedNotificationConsumer[ConnectionIO]] =
    Resource.pure(new PostgresAccountDeletedNotificationConsumerStore(recipient))

private class PostgresAccountDeletedNotificationProducerStore() extends AccountDeletedNotificationProducer[ConnectionIO]:
  override def produceEvent(notification: AccountDeletedNotification.ToNotify): ConnectionIO[Notified] =
    NotificationStatements.insertNotification(notification)

private class PostgresAccountDeletedNotificationConsumerStore(recipient: Recipient)
    extends AccountDeletedNotificationConsumer[ConnectionIO]:
  override def consumeEvent(): ConnectionIO[Option[AccountDeletedNotification.Notified]] =
    NotificationStatements.claimNextEvent(recipient).option

private object NotificationStatements:
  def claimNextEvent(recipient: Recipient): Query0[Notified] =
    sql"""
         |DELETE FROM auth.account_deleted_notifications
         |WHERE uuid = (
         |  SELECT uuid
         |  FROM auth.account_deleted_notifications
         |  WHERE recipient = $recipient
         |  FOR UPDATE SKIP LOCKED
         |  LIMIT 1
         |)
         |RETURNING *
       """.stripMargin.query[Notified]

  def insertNotification(event: ToNotify): ConnectionIO[Notified] =
    sql"""
         |INSERT INTO auth.account_deleted_notifications (
         |  user_id,
         |  recipient
         |) VALUES (
         |  ${event.userId},
         |  ${event.recipient}
         |)
       """.stripMargin.update
      .withUniqueGeneratedKeys[Notified]("uuid", "user_id", "recipient")
