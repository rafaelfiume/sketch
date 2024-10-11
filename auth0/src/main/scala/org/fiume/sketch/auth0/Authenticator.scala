package org.fiume.sketch.auth0

import cats.effect.{Clock, Sync}
import cats.implicits.*
import org.fiume.sketch.shared.app.algebras.Store.syntax.*
import org.fiume.sketch.shared.auth0.algebras.UsersStore
import org.fiume.sketch.shared.auth0.domain.{AuthenticationError, Jwt, JwtError, User}
import org.fiume.sketch.shared.auth0.domain.AuthenticationError.*
import org.fiume.sketch.shared.auth0.domain.Passwords.{HashedPassword, PlainPassword}
import org.fiume.sketch.shared.auth0.domain.User.Username

import java.security.{PrivateKey, PublicKey}
import scala.concurrent.duration.Duration

trait Authenticator[F[_]]:
  def authenticate(username: Username, password: PlainPassword): F[Either[AuthenticationError, Jwt]]
  def verify(jwt: Jwt): Either[JwtError, User]

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
      override def authenticate(username: Username, password: PlainPassword): F[Either[AuthenticationError, Jwt]] =
        for
          account <- store.fetchAccount(username).commit()
          jwt <- account match
            case None =>
              UserNotFoundError.asLeft.pure[F]

            case Some(account) if !account.isActive =>
              AccountNotActiveError.asLeft.pure[F]

            case Some(account) =>
              HashedPassword
                .verifyPassword(password, account.credentials.hashedPassword)
                .ifM(
                  ifTrue = clock.realTimeInstant.map { now =>
                    JwtIssuer.make(privateKey, User(account.uuid, username), now, expirationOffset).asRight
                  },
                  ifFalse = InvalidPasswordError.asLeft.pure[F]
                )
        yield jwt

      override def verify(jwt: Jwt): Either[JwtError, User] =
        JwtIssuer.verify(jwt, publicKey)
  }
