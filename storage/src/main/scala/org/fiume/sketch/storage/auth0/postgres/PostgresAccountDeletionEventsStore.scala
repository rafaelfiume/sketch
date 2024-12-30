package org.fiume.sketch.storage.auth.postgres

import cats.effect.{Async, Resource}
import cats.implicits.*
import doobie.*
import doobie.free.connection.ConnectionIO
import doobie.implicits.*
import doobie.postgres.implicits.*
import org.fiume.sketch.shared.auth.UserId
import org.fiume.sketch.shared.auth.accounts.{
  AccountDeletionEvent,
  AccountDeletionEventConsumer,
  CancellableAccountDeletionEventProducer
}
import org.fiume.sketch.shared.auth.accounts.AccountDeletionEvent.{Scheduled, ToSchedule}
import org.fiume.sketch.storage.auth.postgres.DatabaseCodecs.given

object PostgresAccountDeletionEventsStore:
  def make[F[_]: Async](): Resource[F, PostgresAccountDeletionEventsStore] =
    Resource.pure[F, PostgresAccountDeletionEventsStore](new PostgresAccountDeletionEventsStore())

private class PostgresAccountDeletionEventsStore private ()
    extends CancellableAccountDeletionEventProducer[ConnectionIO]
    with AccountDeletionEventConsumer[ConnectionIO]:

  override def produceEvent(accountDeletion: ToSchedule): ConnectionIO[Scheduled] =
    EventStatements.insertPermanentDeletionEvent(accountDeletion)

  override def cancelEventById(userId: UserId): ConnectionIO[Unit] = EventStatements.deleteEvent(userId).run.void

  override def consumeEvent(): ConnectionIO[Option[AccountDeletionEvent.Scheduled]] =
    EventStatements.claimNextEvent().option

private object EventStatements:
  def claimNextEvent(): Query0[AccountDeletionEvent.Scheduled] =
    // Writing the same query with CTE would be equally doable
    sql"""
         |DELETE FROM auth.account_deletion_scheduled_events
         |WHERE uuid = (
         |  SELECT uuid
         |  FROM auth.account_deletion_scheduled_events
         |  WHERE permanent_deletion_at < now()
         |  FOR UPDATE SKIP LOCKED
         |  LIMIT 1
         |)
         |RETURNING *
    """.stripMargin.query[AccountDeletionEvent.Scheduled]

  def insertPermanentDeletionEvent(event: ToSchedule): ConnectionIO[Scheduled] =
    sql"""
         |INSERT INTO auth.account_deletion_scheduled_events (
         |  user_id,
         |  permanent_deletion_at
         |) VALUES (
         |  ${event.userId},
         |  ${event.permanentDeletionAt}
         |)
       """.stripMargin.update
      .withUniqueGeneratedKeys[Scheduled]("uuid", "user_id", "permanent_deletion_at")

  def deleteEvent(userId: UserId): Update0 =
    sql"""
         |DELETE FROM auth.account_deletion_scheduled_events
         |WHERE user_id = $userId
       """.stripMargin.update
