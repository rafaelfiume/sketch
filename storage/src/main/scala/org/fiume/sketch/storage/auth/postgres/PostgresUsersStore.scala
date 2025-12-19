package org.fiume.sketch.storage.auth.postgres

import cats.effect.Resource
import cats.implicits.*
import doobie.*
import doobie.free.connection.ConnectionIO
import doobie.implicits.*
import doobie.postgres.implicits.*
import org.fiume.sketch.shared.auth.Passwords.{HashedPassword, Salt}
import org.fiume.sketch.shared.auth.User.*
import org.fiume.sketch.shared.auth.UserId
import org.fiume.sketch.shared.auth.accounts.{Account, AccountState}
import org.fiume.sketch.shared.auth.algebras.UsersStore
import org.fiume.sketch.storage.auth.postgres.DatabaseCodecs.given
import org.fiume.sketch.storage.auth.postgres.Statements.*

object PostgresUsersStore:
  def make[F[_]](): Resource[F, UsersStore[ConnectionIO]] =
    Resource.pure(new PostgresUsersStore[F]())

private class PostgresUsersStore[F[_]] private () extends UsersStore[ConnectionIO]:

  override def createAccount(credentials: UserCredentials): ConnectionIO[UserId] =
    insertUserCredentials(credentials.username, credentials.hashedPassword, credentials.salt)

  override def fetchAccount(userId: UserId): ConnectionIO[Option[Account]] =
    Statements.selectUserAccount(userId).option

  override def fetchAccount(username: Username): ConnectionIO[Option[Account]] =
    Statements.selectUserAccount(username).option

  override def updatePassword(userId: UserId, password: HashedPassword): ConnectionIO[Unit] =
    Statements.updatePassword(userId, password).run.void

  override def deleteAccount(userId: UserId): ConnectionIO[Option[UserId]] = Statements.delete(userId).option

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
         |  soft_deleted_at
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
         |  soft_deleted_at
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
      case AccountState.Active(since) =>
        fr"state = 'Active', activated_at = $since"
      case AccountState.SoftDeleted(at) =>
        fr"state = 'PendingDeletion', soft_deleted_at = $at"

    fr"""
         |UPDATE auth.users
         |SET
         |  username = ${account.credentials.username},
         |  password_hash = ${account.credentials.hashedPassword},
         |  salt = ${account.credentials.salt},
         |  $stateUpdate
         |WHERE uuid = ${account.uuid}
    """.stripMargin.update

  def delete(userId: UserId): Query0[UserId] =
    sql"DELETE FROM auth.users u WHERE u.uuid = $userId RETURNING u.uuid".query
