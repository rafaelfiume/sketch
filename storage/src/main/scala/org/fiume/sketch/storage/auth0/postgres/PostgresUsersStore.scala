package org.fiume.sketch.storage.auth0.postgres

import cats.effect.{Async, Resource}
import cats.effect.kernel.Clock
import cats.implicits.*
import cats.~>
import doobie.*
import doobie.free.connection.ConnectionIO
import doobie.implicits.*
import doobie.postgres.implicits.*
import org.fiume.sketch.shared.auth0.algebras.UsersStore
import org.fiume.sketch.shared.auth0.domain.{Account, Passwords, User, UserId}
import org.fiume.sketch.shared.auth0.domain.Passwords.{HashedPassword, Salt}
import org.fiume.sketch.shared.auth0.domain.User.*
import org.fiume.sketch.shared.auth0.jobs.ScheduledAccountDeletion
import org.fiume.sketch.shared.jobs.JobId
import org.fiume.sketch.storage.auth0.postgres.DatabaseCodecs.given
import org.fiume.sketch.storage.auth0.postgres.Statements.*
import org.fiume.sketch.storage.postgres.AbstractPostgresStore

import java.time.Instant

object PostgresUsersStore:
  def make[F[_]: Async](tx: Transactor[F], clock: Clock[F]): Resource[F, PostgresUsersStore[F]] =
    WeakAsync.liftK[F, ConnectionIO].map(lift => new PostgresUsersStore[F](lift, tx, clock))

private class PostgresUsersStore[F[_]: Async] private (lift: F ~> ConnectionIO, tx: Transactor[F], clock: Clock[F])
    extends AbstractPostgresStore[F](lift, tx)
    with UsersStore[F, ConnectionIO]:

  override def store(credentials: UserCredentials): ConnectionIO[UserId] =
    insertUserCredentials(credentials.username, credentials.hashedPassword, credentials.salt)

  override def fetchAccount(uuid: UserId): ConnectionIO[Option[Account]] = ???

  override def fetchAccount(username: Username): ConnectionIO[Option[Account]] =
    Statements.selectUserAccount(username).option

  override def fetchCredentials(username: Username): ConnectionIO[Option[UserCredentialsWithId]] =
    Statements.selectUserCredential(username).option

  override def updatePassword(uuid: UserId, password: HashedPassword): ConnectionIO[Unit] =
    Statements.updatePassword(uuid, password).run.void

  override def delete(uuid: UserId): ConnectionIO[Unit] = Statements.delete(uuid).run.void

  override def activateAccount(uuid: UserId): ConnectionIO[Instant] = ???

  override def claimNextJob(): ConnectionIO[Option[ScheduledAccountDeletion]] = JobStatements.lockAndRemoveNextJob().option

  override protected def softDeleteAccount(uuid: UserId): ConnectionIO[Instant] =
    lift(clock.realTimeInstant).flatTap { Statements.updateSoftDeletion(uuid, _).run.void }

  override protected[postgres] def schedulePermanentDeletion(
    userId: UserId,
    permanentDeletionAt: Instant
  ): ConnectionIO[ScheduledAccountDeletion] =
    JobStatements.insertPermanentDeletionJob(userId, permanentDeletionAt)

private object Statements:
  def insertUserCredentials(username: Username, password: HashedPassword, salt: Salt): ConnectionIO[UserId] =
    sql"""
         |INSERT INTO auth.users (
         |  username,
         |  password_hash,
         |  salt
         |) VALUES (
         |  $username,
         |  $password,
         |  $salt
         |)
    """.stripMargin.update.withUniqueGeneratedKeys[UserId]("uuid")

  def selectUserAccount(username: Username): Query0[Account] =
    sql"""
         |SELECT
         |  uuid,
         |  username,
         |  password_hash,
         |  salt,
         |  state,
         |  created_at,
         |  deleted_at
         |FROM auth.users
         |WHERE username = $username
    """.stripMargin.query

  def selectUserCredential(username: Username): Query0[UserCredentialsWithId] =
    sql"""
         |SELECT
         |  uuid,
         |  username,
         |  password_hash,
         |  salt
         |FROM auth.users
         |WHERE username = $username
    """.stripMargin.query

  def updatePassword(uuid: UserId, password: HashedPassword): Update0 =
    sql"""
         |UPDATE auth.users
         |SET password_hash = $password
         |WHERE uuid = $uuid
    """.stripMargin.update

  def updateSoftDeletion(uuid: UserId, deletedAt: Instant): Update0 =
    sql"""
         |UPDATE auth.users
         |SET
         |  state = 'PendingDeletion',
         |  deleted_at = $deletedAt
         |WHERE uuid = $uuid
    """.stripMargin.update

  def delete(uuid: UserId): Update0 =
    sql"DELETE FROM auth.users WHERE uuid = $uuid".update

private object JobStatements:
  def insertPermanentDeletionJob(uuid: UserId, permanentDeletionAt: Instant): ConnectionIO[ScheduledAccountDeletion] =
    sql"""
         |INSERT INTO auth.account_permanent_deletion_queue (
         |  user_id,
         |  permanent_deletion_at
         |) VALUES (
         |  $uuid,
         |  $permanentDeletionAt
         |)
    """.stripMargin.update
      .withUniqueGeneratedKeys[ScheduledAccountDeletion]("uuid", "user_id", "permanent_deletion_at")

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
