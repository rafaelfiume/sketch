package org.fiume.sketch.storage.auth0.postgres

import cats.effect.{Async, Clock, Resource}
import cats.implicits.*
import cats.~>
import doobie.*
import doobie.free.connection.ConnectionIO
import doobie.implicits.*
import doobie.postgres.implicits.*
import org.fiume.sketch.shared.auth0.Model.*
import org.fiume.sketch.shared.auth0.Passwords
import org.fiume.sketch.shared.auth0.Passwords.{HashedPassword, Salt}
import org.fiume.sketch.shared.auth0.algebras.UsersStore
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

  def store(username: Username, password: HashedPassword, salt: Salt): ConnectionIO[UUID] =
    insertUserCredentials(username, password, salt)
      .withUniqueGeneratedKeys[UUID](
        "uuid"
      )

  def fetchUser(uuid: UUID): ConnectionIO[Option[User]] = selectUser(uuid).option

  def fetchCredentials(username: Username): ConnectionIO[Option[Credentials]] =
    Statements.selectUserCredential(username).option

  def updatePassword(uuid: UUID, password: HashedPassword): ConnectionIO[Unit] =
    Statements.updatePassword(uuid, password).run.void
  def delete(uuid: UUID): ConnectionIO[Unit] = Statements.deleteUser(uuid).run.void

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

  def selectUser(uuid: UUID): Query0[User] =
    sql"""
         |SELECT
         |  uuid,
         |  username
         |FROM auth.users
         |WHERE uuid = $uuid
    """.stripMargin.query

  def selectUserCredential(username: Username): Query0[Credentials] =
    sql"""
         |SELECT
         |  uuid,
         |  username,
         |  password_hash
         |FROM auth.users
         |WHERE username = $username
    """.stripMargin.query

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