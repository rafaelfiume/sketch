package org.fiume.sketch.auth0

import cats.effect.{Clock, Sync}
import cats.implicits.*
import org.fiume.sketch.auth0.AuthenticationError.*
import org.fiume.sketch.shared.app.syntax.StoreSyntax.*
import org.fiume.sketch.shared.auth0.algebras.UsersStore
import org.fiume.sketch.shared.auth0.domain.{AccountState, JwtError, JwtToken, User}
import org.fiume.sketch.shared.auth0.domain.Passwords.{HashedPassword, PlainPassword}
import org.fiume.sketch.shared.auth0.domain.User.Username

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

  case class AccountNotActiveError private (state: AccountState) extends AuthenticationError:
    override def details: String = s"Account is not active: $state"

  object AccountNotActiveError:
    def make(state: AccountState): AccountNotActiveError =
      // checking invariant first
      if state.isInstanceOf[AccountState.Active] then throw new IllegalArgumentException("Account is active")
      else AccountNotActiveError(state)

trait Authenticator[F[_]]:
  def authenticate(username: Username, password: PlainPassword): F[Either[AuthenticationError, JwtToken]]
  def verify(jwtToken: JwtToken): Either[JwtError, User]

object Authenticator:
  def make[F[_]: Sync, Txn[_]](
    clock: Clock[F],
    store: UsersStore[F, Txn],
    privateKey: PrivateKey,
    publicKey: PublicKey,
    expirationOffset: Duration
  ): F[Authenticator[F]] = Sync[F].delay {
    given UsersStore[F, Txn] = store

    new Authenticator[F]:
      override def authenticate(username: Username, password: PlainPassword): F[Either[AuthenticationError, JwtToken]] =
        for
          account <- store.fetchAccount(username).commit()
          jwtToken <- account match
            case None =>
              UserNotFoundError.asLeft.pure[F]

            case Some(account) if !account.isActive =>
              AccountNotActiveError.make(account.state).asLeft.pure[F]

            case Some(account) =>
              HashedPassword
                .verifyPassword(password, account.credentials.hashedPassword)
                .ifM(
                  ifTrue = clock.realTimeInstant.map { now =>
                    JwtIssuer.make(privateKey, User(account.uuid, username), now, expirationOffset).asRight
                  },
                  ifFalse = InvalidPasswordError.asLeft.pure[F]
                )
        yield jwtToken

      override def verify(jwtToken: JwtToken): Either[JwtError, User] =
        JwtIssuer.verify(jwtToken, publicKey)
  }
