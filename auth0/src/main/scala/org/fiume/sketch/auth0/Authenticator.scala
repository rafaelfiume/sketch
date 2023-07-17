package org.fiume.sketch.auth0

import cats.FlatMap
import cats.effect.Sync
import cats.implicits.*
import org.fiume.sketch.auth0.JwtToken
import org.fiume.sketch.shared.auth0.Model.{User, Username}
import org.fiume.sketch.shared.auth0.Passwords.{HashedPassword, PlainPassword}
import org.fiume.sketch.shared.auth0.algebras.UsersStore

import java.security.PrivateKey
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

trait Authenticator[F[_]]:
  // TODO More refined error types than String
  def authenticate(username: Username, password: PlainPassword): F[Either[String, JwtToken]]

object Authenticator:
  def make[F[_]: Sync, Txn[_]: FlatMap](
    store: UsersStore[F, Txn],
    privateKey: PrivateKey,
    expirationOffset: Duration
  ): F[Authenticator[F]] = Sync[F].delay {
    new Authenticator[F]:
      override def authenticate(username: Username, password: PlainPassword): F[Either[String, JwtToken]] =
        for
          credentials <- store.commit { store.fetchCredential(username) } // TODO User ccommit syntax
          jwtToken <- credentials match
            case None =>
              Sync[F].pure(Left("User not found"))
            case Some(creds) =>
              if HashedPassword.verifyPassword(password, creds.hashedPassword) then
                JwtToken.createJwtToken[F](privateKey, User(creds.uuid, username), expirationOffset).map(Right(_))
              else Sync[F].pure(Left("Invalid password"))
        yield jwtToken
  }
