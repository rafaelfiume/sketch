package org.fiume.sketch.storage.auth0.postgres

import cats.effect.{Async, Clock, Resource}
import cats.implicits.*
import cats.~>
import doobie.*
import doobie.free.connection.ConnectionIO
import doobie.implicits.*
import doobie.postgres.implicits.*
import org.fiume.sketch.storage.auth0.Model.*
import org.fiume.sketch.storage.auth0.Passwords.{HashedPassword, Salt}
import org.fiume.sketch.storage.auth0.algebras.UsersStore
import org.fiume.sketch.storage.auth0.postgres.DoobieMappings.given
import org.fiume.sketch.storage.auth0.postgres.Statements.*
import org.fiume.sketch.storage.postgres.AbstractPostgresStore

import java.util.UUID

object PostgresUsersStore:
  def make[F[_]: Async](tx: Transactor[F]): Resource[F, PostgresUsersStore[F]] =
    WeakAsync.liftK[F, ConnectionIO].map(l => new PostgresUsersStore[F](l, tx))

private class PostgresUsersStore[F[_]: Async] private (l: F ~> ConnectionIO, tx: Transactor[F])
    extends AbstractPostgresStore[F](l, tx)
    with UsersStore[F, ConnectionIO]:

  def store(user: User, password: HashedPassword, salt: Salt): ConnectionIO[UUID] =
    insertUserCredentials(user, password, salt)
      .withUniqueGeneratedKeys[UUID](
        "uuid"
      )

  def fetchCredentials(uuid: UUID): ConnectionIO[Option[UserCredentials]] = selectUserCredentials(uuid).option
  def fetchUser(uuid: UUID): ConnectionIO[Option[User]] = Statements.selectUser(uuid).option
  def updateUser(uuid: UUID, user: User): ConnectionIO[Unit] = Statements.updateUser(uuid, user).run.void
  def updatePassword(uuid: UUID, password: HashedPassword): ConnectionIO[Unit] =
    Statements.updatePassword(uuid, password).run.void
  def delete(uuid: UUID): ConnectionIO[Unit] = Statements.deleteUser(uuid).run.void

private object Statements:
  def insertUserCredentials(user: User, password: HashedPassword, salt: Salt): Update0 =
    sql"""
         |INSERT INTO auth.users (
         |  password_hash,
         |  salt,
         |  username,
         |  first_name,
         |  last_name,
         |  email
         |) VALUES (
         |  $password,
         |  $salt,
         |  ${user.username},
         |  ${user.name.first},
         |  ${user.name.last},
         |  ${user.email}
         |)
    """.stripMargin.update

  def selectUser(uuid: UUID): Query0[User] =
    sql"""
         |SELECT
         |  username,
         |  first_name,
         |  last_name,
         |  email
         |FROM auth.users
         |WHERE uuid = $uuid
    """.stripMargin.query

  def selectUserCredentials(uuid: UUID): Query0[UserCredentials] =
    sql"""
         |SELECT
         |  uuid,
         |  password_hash,
         |  salt,
         |  username,
         |  first_name,
         |  last_name,
         |  email,
         |  created_at,
         |  updated_at
         |FROM auth.users
         |WHERE uuid = $uuid
    """.stripMargin.query

  def updateUser(uuid: UUID, user: User): Update0 =
    sql"""
         |UPDATE auth.users
         |SET
         |  username = ${user.username},
         |  first_name = ${user.name.first},
         |  last_name = ${user.name.last},
         |  email = ${user.email}
         |WHERE uuid = $uuid
    """.stripMargin.update

  def updatePassword(uuid: UUID, password: HashedPassword): Update0 =
    sql"""
         |UPDATE auth.users
         |SET
         |  password_hash = $password
         |WHERE uuid = $uuid
    """.stripMargin.update

  def deleteUser(uuid: UUID): Update0 =
    sql"""
         |DELETE FROM auth.users
         |WHERE uuid = $uuid
    """.stripMargin.update
