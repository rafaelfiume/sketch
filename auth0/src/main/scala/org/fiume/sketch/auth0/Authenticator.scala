package org.fiume.sketch.auth0

import cats.FlatMap
import cats.effect.{Clock, Sync}
import cats.implicits.*
import io.circe.ParsingFailure
import org.fiume.sketch.auth0.AuthenticationError.*
import org.fiume.sketch.auth0.Authenticator.*
import org.fiume.sketch.auth0.JwtToken
import org.fiume.sketch.shared.auth0.Passwords.{HashedPassword, PlainPassword}
import org.fiume.sketch.shared.auth0.User
import org.fiume.sketch.shared.auth0.User.Username
import org.fiume.sketch.shared.auth0.algebras.UsersStore
import pdi.jwt.exceptions.*

import java.security.{PrivateKey, PublicKey}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration
import scala.util.control.NoStackTrace

sealed trait AuthenticationError extends Throwable with NoStackTrace:
  def details: String

object AuthenticationError:
  case object UserNotFoundError extends AuthenticationError:
    override def details: String = "User not found"

  case object InvalidPasswordError extends AuthenticationError:
    override def details: String = "Invalid password"

  case class JwtExpirationError(details: String) extends AuthenticationError
  case class JwtEmptySignatureError(details: String) extends AuthenticationError
  case class JwtInvalidTokenError(details: String) extends AuthenticationError
  case class JwtValidationError(details: String) extends AuthenticationError
  case class JwtUnknownError(details: String) extends AuthenticationError

trait Authenticator[F[_]]:
  def authenticate(username: Username, password: PlainPassword): F[Either[AuthenticationError, JwtToken]]
  def verify(jwtToken: JwtToken): Either[AuthenticationError, User] // TODO Verify string instead of JwtToken?

object Authenticator:
  def make[F[_], Txn[_]: FlatMap](
    store: UsersStore[F, Txn],
    privateKey: PrivateKey,
    publicKey: PublicKey,
    expirationOffset: Duration
  )(using F: Sync[F], clock: Clock[F]): F[Authenticator[F]] = F.delay {
    new Authenticator[F]:
      override def authenticate(username: Username, password: PlainPassword): F[Either[AuthenticationError, JwtToken]] =
        for
          credentials <- store.commit { store.fetchCredentials(username) } // TODO User ccommit syntax
          jwtToken <- credentials match
            case None =>
              UserNotFoundError.asLeft.pure[F]
            case Some(creds) =>
              if HashedPassword.verifyPassword(password, creds.hashedPassword) then
                JwtToken.createJwtToken(privateKey, User(creds.uuid, username), expirationOffset)(using F, clock).map(_.asRight)
              else InvalidPasswordError.asLeft.pure[F]
        yield jwtToken

      override def verify(jwtToken: JwtToken): Either[AuthenticationError, User] =
        JwtToken.verifyJwtToken(jwtToken, publicKey).leftMap(mapJwtErrors)

      private def mapJwtErrors(jwtError: Throwable): AuthenticationError =
        jwtError match
          case e: JwtEmptySignatureException => JwtEmptySignatureError(e.getMessage)
          case e: ParsingFailure             => JwtInvalidTokenError(s"Invalid Jwt token: ${e.getMessage}")
          case e: JwtValidationException     => JwtValidationError(e.getMessage)
          case e: JwtExpirationException     => JwtExpirationError(e.getMessage)
          case e                             => JwtUnknownError(e.getMessage)
  }
