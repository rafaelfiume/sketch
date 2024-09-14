package org.fiume.sketch.storage.auth0.postgres

import cats.effect.{Async, Resource}
import cats.implicits.*
import cats.~>
import doobie.*
import doobie.free.connection.ConnectionIO
import doobie.implicits.*
import doobie.postgres.implicits.*
import org.fiume.sketch.shared.auth0.{Account, Passwords, User, UserId}
import org.fiume.sketch.shared.auth0.Passwords.{HashedPassword, Salt}
import org.fiume.sketch.shared.auth0.User.*
import org.fiume.sketch.shared.auth0.algebras.UsersStore
import org.fiume.sketch.storage.auth0.postgres.DoobieMappings.given
import org.fiume.sketch.storage.auth0.postgres.Statements.*
import org.fiume.sketch.storage.postgres.AbstractPostgresStore

import java.time.Instant

object PostgresUsersStore:
  def make[F[_]: Async](tx: Transactor[F]): Resource[F, PostgresUsersStore[F]] =
    WeakAsync.liftK[F, ConnectionIO].map(l => new PostgresUsersStore[F](l, tx))

private class PostgresUsersStore[F[_]: Async] private (l: F ~> ConnectionIO, tx: Transactor[F])
    extends AbstractPostgresStore[F](l, tx)
    with UsersStore[F, ConnectionIO]:

  override def store(credentials: UserCredentials): ConnectionIO[UserId] =
    insertUserCredentials(credentials.username, credentials.hashedPassword, credentials.salt)
      .withUniqueGeneratedKeys[UserId](
        "uuid"
      )

  override def fetchAccount(username: Username): ConnectionIO[Option[Account]] =
    Statements.selectUserAccount(username).option

  override def fetchCredentials(username: Username): ConnectionIO[Option[UserCredentialsWithId]] =
    Statements.selectUserCredential(username).option

  override def updatePassword(uuid: UserId, password: HashedPassword): ConnectionIO[Unit] =
    Statements.updatePassword(uuid, password).run.void

  override def markForDeletion(uuid: UserId): ConnectionIO[Unit] =
    Statements.updateSoftDeletion(uuid, Instant.now()).run.void

private object Statements:
  def insertUserCredentials(username: Username, password: HashedPassword, salt: Salt): Update0 =
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
    """.stripMargin.update

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
