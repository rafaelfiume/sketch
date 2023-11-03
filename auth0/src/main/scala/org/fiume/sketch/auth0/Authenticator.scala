package org.fiume.sketch.auth0

import cats.effect.{Clock, Sync}
import cats.implicits.*
import org.fiume.sketch.auth0.AuthenticationError.*
import org.fiume.sketch.shared.auth0.Passwords.{HashedPassword, PlainPassword}
import org.fiume.sketch.shared.auth0.User
import org.fiume.sketch.shared.auth0.User.Username
import org.fiume.sketch.shared.auth0.algebras.UsersStore

import java.security.{PrivateKey, PublicKey}
import scala.concurrent.duration.Duration
import scala.util.control.NoStackTrace

sealed trait AuthenticationError extends Throwable with NoStackTrace:
  def details: String

object AuthenticationError:
  case object UserNotFoundError extends AuthenticationError:
    override def details: String = "User not found"

  case object InvalidPasswordError extends AuthenticationError:
    override def details: String = "Invalid password"

trait Authenticator[F[_]]:
  def authenticate(username: Username, password: PlainPassword): F[Either[AuthenticationError, JwtToken]]
  def verify(jwtToken: JwtToken): Either[JwtError, User]

object Authenticator:
  def make[F[_], Txn[_]](
    store: UsersStore[F, Txn],
    privateKey: PrivateKey,
    publicKey: PublicKey,
    expirationOffset: Duration
  )(using F: Sync[F], clock: Clock[F]): F[Authenticator[F]] = F.delay {
    new Authenticator[F]:
      override def authenticate(username: Username, password: PlainPassword): F[Either[AuthenticationError, JwtToken]] =
        for
          credentials <- store.commit { store.fetchCredentials(username) }
          jwtToken <- credentials match
            case None =>
              UserNotFoundError.asLeft.pure[F]
            case Some(creds) =>
              if HashedPassword.verifyPassword(password, creds.hashedPassword) then
                JwtToken.makeJwtToken(privateKey, User(creds.uuid, username), expirationOffset)(using F, clock).map(_.asRight)
              else InvalidPasswordError.asLeft.pure[F]
        yield jwtToken

      override def verify(jwtToken: JwtToken): Either[JwtError, User] =
        JwtToken.verifyJwtToken(jwtToken, publicKey)
  }
