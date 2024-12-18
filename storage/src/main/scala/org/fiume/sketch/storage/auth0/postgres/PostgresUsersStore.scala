package org.fiume.sketch.storage.auth.postgres

import cats.effect.{Async, Resource}
import cats.implicits.*
import cats.~>
import doobie.*
import doobie.free.connection.ConnectionIO
import doobie.implicits.*
import doobie.postgres.implicits.*
import org.fiume.sketch.shared.auth.{Passwords, UserId}
import org.fiume.sketch.shared.auth.Passwords.{HashedPassword, Salt}
import org.fiume.sketch.shared.auth.User.*
import org.fiume.sketch.shared.auth.accounts.{Account, AccountState}
import org.fiume.sketch.shared.auth.algebras.UsersStore
import org.fiume.sketch.storage.auth.postgres.DatabaseCodecs.given
import org.fiume.sketch.storage.auth.postgres.Statements.*
import org.fiume.sketch.storage.postgres.AbstractPostgresStore

object PostgresUsersStore:
  def make[F[_]: Async](tx: Transactor[F]): Resource[F, PostgresUsersStore[F]] =
    WeakAsync.liftK[F, ConnectionIO].map(lift => new PostgresUsersStore[F](lift, tx))

private class PostgresUsersStore[F[_]: Async] private (lift: F ~> ConnectionIO, tx: Transactor[F])
    extends AbstractPostgresStore[F](lift, tx)
    with UsersStore[F, ConnectionIO]:

  override def createAccount(credentials: UserCredentials): ConnectionIO[UserId] =
    insertUserCredentials(credentials.username, credentials.hashedPassword, credentials.salt)

  override def fetchAccount(userId: UserId): ConnectionIO[Option[Account]] =
    Statements.selectUserAccount(userId).option

  override def fetchAccount(username: Username): ConnectionIO[Option[Account]] =
    Statements.selectUserAccount(username).option

  override def updatePassword(userId: UserId, password: HashedPassword): ConnectionIO[Unit] =
    Statements.updatePassword(userId, password).run.void

  override def deleteAccount(userId: UserId): ConnectionIO[Unit] = Statements.delete(userId).run.void

  override def updateAccount(account: Account): ConnectionIO[Unit] =
    Statements.update(account).run.void

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

  def selectUserAccount(userId: UserId): Query0[Account] =
    sql"""
         |SELECT
         |  uuid,
         |  username,
         |  password_hash,
         |  salt,
         |  state,
         |  activated_at,
         |  deleted_at
         |FROM auth.users
         |WHERE uuid = $userId
    """.stripMargin.query

  def selectUserAccount(username: Username): Query0[Account] =
    sql"""
         |SELECT
         |  uuid,
         |  username,
         |  password_hash,
         |  salt,
         |  state,
         |  activated_at,
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

  def updatePassword(userId: UserId, password: HashedPassword): Update0 =
    sql"""
         |UPDATE auth.users
         |SET password_hash = $password
         |WHERE uuid = $userId
    """.stripMargin.update

  def update(account: Account): Update0 =
    val stateUpdate = account.state match
      case AccountState.Active(activatedAt) =>
        fr"state = 'Active', activated_at = ${activatedAt}"
      case AccountState.SoftDeleted(deletedAt) =>
        fr"state = 'PendingDeletion', deleted_at = ${deletedAt}"

    fr"""
         |UPDATE auth.users
         |SET
         |  username = ${account.credentials.username},
         |  password_hash = ${account.credentials.hashedPassword},
         |  salt = ${account.credentials.salt},
         |  $stateUpdate
         |WHERE uuid = ${account.uuid}
    """.stripMargin.update

  def delete(userId: UserId): Update0 =
    sql"DELETE FROM auth.users WHERE uuid = $userId".update
