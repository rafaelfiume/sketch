package org.fiume.sketch.storage.auth.postgres

import cats.effect.{Async, Resource}
import cats.implicits.*
import doobie.*
import doobie.free.connection.ConnectionIO
import doobie.implicits.*
import doobie.postgres.implicits.*
import org.fiume.sketch.shared.auth.UserId
import org.fiume.sketch.shared.auth.accounts.{AccountDeletionEvent, AccountDeletionEventConsumer, AccountDeletionEventProducer}
import org.fiume.sketch.shared.auth.accounts.AccountDeletionEvent.{Scheduled, Unscheduled}
import org.fiume.sketch.shared.common.events.EventId
import org.fiume.sketch.storage.auth.postgres.DatabaseCodecs.given
import org.fiume.sketch.storage.auth.postgres.Statements.*

import java.time.Instant

object PostgresEventsStore:
  def make[F[_]: Async](): Resource[F, PostgresEventsStore] =
    Resource.pure[F, PostgresEventsStore](new PostgresEventsStore())

private class PostgresEventsStore private ()
    extends AccountDeletionEventProducer[ConnectionIO]
    with AccountDeletionEventConsumer[ConnectionIO]:

  override def produceEvent(accountDeletionEvent: Unscheduled): ConnectionIO[Scheduled] =
    EventStatements.insertPermanentDeletionEvent(accountDeletionEvent)

  override def removeEvent(userId: UserId): ConnectionIO[Unit] = EventStatements.deleteEvent(userId).run.void

  override def consumeEvent(): ConnectionIO[Option[AccountDeletionEvent.Scheduled]] =
    EventStatements.lockAndRemoveNextJob().option

private object EventStatements:
  def lockAndRemoveNextJob(): Query0[AccountDeletionEvent.Scheduled] =
    // Writing the same query with CTE would be equally doable
    // provides exactly-once semantics
    // TODO ordering guarantees?
    sql"""
         |DELETE FROM auth.account_permanent_deletion_queue
         |WHERE uuid = (
         |  SELECT uuid
         |  FROM auth.account_permanent_deletion_queue
         |  WHERE permanent_deletion_at < now()
         |  FOR UPDATE SKIP LOCKED
         |  LIMIT 1
         |)
         |RETURNING *
    """.stripMargin.query[AccountDeletionEvent.Scheduled]

  def insertPermanentDeletionEvent(event: AccountDeletionEvent.Unscheduled): ConnectionIO[AccountDeletionEvent.Scheduled] =
    sql"""
         |INSERT INTO auth.account_permanent_deletion_queue (
         |  user_id,
         |  permanent_deletion_at
         |) VALUES (
         |  ${event.userId},
         |  ${event.permanentDeletionAt}
         |)
    """.stripMargin.update
      .withUniqueGeneratedKeys[AccountDeletionEvent.Scheduled]("uuid", "user_id", "permanent_deletion_at")

  def deleteEvent(userId: UserId): Update0 =
    sql"DELETE FROM auth.account_permanent_deletion_queue WHERE user_id = $userId".update
