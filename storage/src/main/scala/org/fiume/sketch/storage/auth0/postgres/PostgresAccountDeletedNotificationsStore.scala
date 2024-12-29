package org.fiume.sketch.storage.auth.postgres

import cats.effect.{Async, Resource}
import cats.implicits.*
import doobie.*
import doobie.free.connection.ConnectionIO
import doobie.implicits.*
import org.fiume.sketch.shared.auth.UserId
import org.fiume.sketch.shared.auth.accounts.{
  AccountDeletedNotification,
  AccountDeletedNotificationConsumer,
  AccountDeletedNotificationProducer,
  Service
}
import org.fiume.sketch.shared.auth.accounts.AccountDeletedNotification.{Notified, ToNotify}
import org.fiume.sketch.storage.auth.postgres.DatabaseCodecs.given

object PostgresAccountDeletedNotificationsStore:
  def make[F[_]: Async](consumerGroup: Service): Resource[F, PostgresAccountDeletedNotificationsStore] =
    Resource.pure[F, PostgresAccountDeletedNotificationsStore](
      new PostgresAccountDeletedNotificationsStore(consumerGroup)
    )

private class PostgresAccountDeletedNotificationsStore private (consumerGroup: Service)
    extends AccountDeletedNotificationProducer[ConnectionIO]
    with AccountDeletedNotificationConsumer[ConnectionIO]:

  override def produceEvent(notification: AccountDeletedNotification.ToNotify): ConnectionIO[Notified] =
    NotificationStatements.insertNotification(notification)

  override def removeEvent(userId: UserId): ConnectionIO[Unit] = NotificationStatements.deleteNotification(userId).run.void

  override def consumeEvent(): ConnectionIO[Option[AccountDeletedNotification.Notified]] =
    NotificationStatements.claimNextEvent(consumerGroup).option

private object NotificationStatements:
  def claimNextEvent(consumerGroup: Service): Query0[Notified] =
    sql"""
         |DELETE FROM auth.account_deleted_notifications
         |WHERE uuid = (
         |  SELECT uuid
         |  FROM auth.account_deleted_notifications
         |  WHERE service_name = $consumerGroup
         |  FOR UPDATE SKIP LOCKED
         |  LIMIT 1
         |)
         |RETURNING *
       """.stripMargin.query[Notified]

  def insertNotification(event: ToNotify): ConnectionIO[Notified] =
    sql"""
         |INSERT INTO auth.account_deleted_notifications (
         |  user_id,
         |  service_name
         |) VALUES (
         |  ${event.userId},
         |  ${event.target}
         |)
       """.stripMargin.update
      .withUniqueGeneratedKeys[Notified]("uuid", "user_id", "service_name")

  def deleteNotification(userId: UserId): Update0 =
    sql"""
         |DELETE FROM auth.account_deleted_notifications
         |WHERE user_id = $userId
       """.stripMargin.update
