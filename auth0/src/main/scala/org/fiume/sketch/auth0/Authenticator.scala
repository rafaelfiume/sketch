package org.fiume.sketch.auth0

import cats.effect.{Clock, Sync}
import cats.implicits.*
import org.fiume.sketch.auth0.AuthenticationError.*
import org.fiume.sketch.shared.app.algebras.Store.Syntax.commit
import org.fiume.sketch.shared.auth0.{AccountState, User}
import org.fiume.sketch.shared.auth0.Passwords.{HashedPassword, PlainPassword}
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
  def make[F[_], Txn[_]](
    store: UsersStore[F, Txn],
    privateKey: PrivateKey,
    publicKey: PublicKey,
    expirationOffset: Duration
  )(using F: Sync[F], clock: Clock[F]): F[Authenticator[F]] = F.delay {
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
                  ifTrue = JwtToken.makeJwtToken(privateKey, User(account.uuid, username), expirationOffset).map(_.asRight),
                  ifFalse = InvalidPasswordError.asLeft.pure[F]
                )
        yield jwtToken

      override def verify(jwtToken: JwtToken): Either[JwtError, User] =
        JwtToken.verifyJwtToken(jwtToken, publicKey)
  }
