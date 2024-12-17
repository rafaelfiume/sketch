package org.fiume.sketch.storage.auth.postgres

import cats.effect.{Async, Resource}
import doobie.*
import doobie.free.connection.ConnectionIO
import doobie.implicits.*
import doobie.postgres.implicits.*
import org.fiume.sketch.shared.auth.accounts.jobs.ScheduledAccountDeletion
import org.fiume.sketch.shared.common.jobs.{EventConsumer, JobId}
import org.fiume.sketch.storage.auth.postgres.DatabaseCodecs.given
import org.fiume.sketch.storage.auth.postgres.Statements.*

import java.time.Instant

object PostgresEventConsumer:
  def make[F[_]: Async](): Resource[F, PostgresEventConsumer] =
    Resource.pure[F, PostgresEventConsumer](new PostgresEventConsumer())

private class PostgresEventConsumer private () extends EventConsumer[ConnectionIO, ScheduledAccountDeletion]:

  override def claimNextJob(): ConnectionIO[Option[ScheduledAccountDeletion]] = JobStatementss.lockAndRemoveNextJob().option

private object JobStatementss:
  def lockAndRemoveNextJob(): Query0[ScheduledAccountDeletion] =
    // Writing the same query with CTE would be equally doable
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
    """.stripMargin.query[ScheduledAccountDeletion]
