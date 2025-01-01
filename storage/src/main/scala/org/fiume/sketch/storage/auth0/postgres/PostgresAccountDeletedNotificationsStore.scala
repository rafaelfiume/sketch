package org.fiume.sketch.storage.auth.postgres

import cats.effect.{Async, Resource}
import doobie.*
import doobie.free.connection.ConnectionIO
import doobie.implicits.*
import org.fiume.sketch.shared.auth.accounts.{
  AccountDeletedNotification,
  AccountDeletedNotificationConsumer,
  AccountDeletedNotificationProducer,
  Service
}
import org.fiume.sketch.shared.auth.accounts.AccountDeletedNotification.{Notified, ToNotify}
import org.fiume.sketch.storage.auth.postgres.DatabaseCodecs.given

object PostgresAccountDeletedNotificationsStore:
  def makeProducer[F[_]: Async](): Resource[F, AccountDeletedNotificationProducer[ConnectionIO]] =
    Resource.pure(new PostgresAccountDeletedNotificationProducerStore())

  def makeConsumer[F[_]: Async](consumerGroup: Service): Resource[F, AccountDeletedNotificationConsumer[ConnectionIO]] =
    Resource.pure(new PostgresAccountDeletedNotificationConsumerStore(consumerGroup))

private class PostgresAccountDeletedNotificationProducerStore() extends AccountDeletedNotificationProducer[ConnectionIO]:
  override def produceEvent(notification: AccountDeletedNotification.ToNotify): ConnectionIO[Notified] =
    NotificationStatements.insertNotification(notification)

private class PostgresAccountDeletedNotificationConsumerStore(consumerGroup: Service)
    extends AccountDeletedNotificationConsumer[ConnectionIO]:
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
