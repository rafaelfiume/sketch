package org.fiume.sketch.storage.auth0.postgres

import cats.effect.{Async, Clock, Resource}
import cats.implicits.*
import cats.~>
import doobie.*
import doobie.free.connection.ConnectionIO
import doobie.implicits.*
import org.fiume.sketch.storage.auth0.Model.*
import org.fiume.sketch.storage.auth0.Passwords.{HashedPassword, Salt}
import org.fiume.sketch.storage.auth0.algebras.UserStore
import org.fiume.sketch.storage.auth0.postgres.DoobieMappings.given
import org.fiume.sketch.storage.auth0.postgres.Statements.*
import org.fiume.sketch.storage.postgres.AbstractPostgresStore

object PostgresUserStore:
  def make[F[_]: Async](tx: Transactor[F]): Resource[F, PostgresUserStore[F]] =
    WeakAsync.liftK[F, ConnectionIO].map(l => new PostgresUserStore[F](l, tx))

private class PostgresUserStore[F[_]: Async] private (l: F ~> ConnectionIO, tx: Transactor[F])
    extends AbstractPostgresStore[F](l, tx)
    with UserStore[F, ConnectionIO]:

  def store(user: User, password: HashedPassword, salt: Salt): ConnectionIO[UserCredentials] =
    insertUserCredentials(user, password, salt)
      .withUniqueGeneratedKeys[UserCredentials](
        "id",
        "password_hash",
        "salt",
        "username",
        "first_name",
        "last_name",
        "email",
        "created_at",
        "updated_at"
      )

  def fetchCredentials(username: Username): ConnectionIO[Option[UserCredentials]] = selectUserCredentials(username).option
  def fetchUser(username: Username): ConnectionIO[Option[User]] = Statements.selectUser(username).option
  def updateUser(user: User): ConnectionIO[Unit] = Statements.updateUser(user).run.void
  def updatePassword(username: Username, password: HashedPassword): ConnectionIO[Unit] =
    Statements.updatePassword(username, password).run.void
  def remove(username: Username): ConnectionIO[Unit] = Statements.deleteUser(username).run.void

private object Statements:
  def insertUserCredentials(user: User, password: HashedPassword, salt: Salt): Update0 =
    sql"""
         |INSERT INTO users (
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

  def selectUser(username: Username): Query0[User] =
    sql"""
         |SELECT
         |  username,
         |  first_name,
         |  last_name,
         |  email
         |FROM users
         |WHERE username = $username
    """.stripMargin.query

  def selectUserCredentials(username: Username): Query0[UserCredentials] =
    sql"""
         |SELECT
         |  id,
         |  password_hash,
         |  salt,
         |  username,
         |  first_name,
         |  last_name,
         |  email,
         |  created_at,
         |  updated_at
         |FROM users
         |WHERE username = $username
    """.stripMargin.query

  def updateUser(user: User): Update0 =
    sql"""
         |UPDATE users
         |SET
         |  first_name = ${user.name.first},
         |  last_name = ${user.name.last},
         |  email = ${user.email}
         |WHERE username = ${user.username}
    """.stripMargin.update

  def updatePassword(username: Username, password: HashedPassword): Update0 =
    sql"""
         |UPDATE users
         |SET
         |  password_hash = $password
         |WHERE username = $username
    """.stripMargin.update

  def deleteUser(username: Username): Update0 =
    sql"""
         |DELETE FROM users
         |WHERE username = $username
    """.stripMargin.update
