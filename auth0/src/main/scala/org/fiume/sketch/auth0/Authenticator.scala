package org.fiume.sketch.auth0

import cats.FlatMap
import cats.effect.{Clock, Sync}
import cats.implicits.*
import org.fiume.sketch.auth0.JwtToken
import org.fiume.sketch.shared.auth0.Model.{User, Username}
import org.fiume.sketch.shared.auth0.Passwords.{HashedPassword, PlainPassword}
import org.fiume.sketch.shared.auth0.algebras.UsersStore

import java.security.{PrivateKey, PublicKey}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

trait Authenticator[F[_]]:
  // TODO More refined error types than String
  def authenticate(username: Username, password: PlainPassword): F[Either[String, JwtToken]]
  def verify(jwtToken: JwtToken): Either[Throwable, User]

object Authenticator:
  def make[F[_], Txn[_]: FlatMap](
    store: UsersStore[F, Txn],
    privateKey: PrivateKey,
    publicKey: PublicKey,
    expirationOffset: Duration
  )(using F: Sync[F], clock: Clock[F]): F[Authenticator[F]] = F.delay {
    new Authenticator[F]:
      override def authenticate(username: Username, password: PlainPassword): F[Either[String, JwtToken]] =
        for
          credentials <- store.commit { store.fetchCredentials(username) } // TODO User ccommit syntax
          jwtToken <- credentials match
            case None =>
              Sync[F].pure(Left("User not found"))
            case Some(creds) =>
              if HashedPassword.verifyPassword(password, creds.hashedPassword) then
                JwtToken.createJwtToken(privateKey, User(creds.uuid, username), expirationOffset)(using F, clock).map(_.asRight)
              else Sync[F].pure(Left("Invalid password"))
        yield jwtToken

      override def verify(jwtToken: JwtToken): Either[Throwable, User] =
        JwtToken.verifyJwtToken(jwtToken, publicKey)
  }
