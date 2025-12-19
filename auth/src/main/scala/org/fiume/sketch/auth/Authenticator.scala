package org.fiume.sketch.auth

import cats.effect.{Clock, Sync}
import cats.implicits.*
import org.fiume.sketch.shared.auth.{AuthenticationError, Jwt, JwtVerificationError, User}
import org.fiume.sketch.shared.auth.AuthenticationError.*
import org.fiume.sketch.shared.auth.Passwords.{HashedPassword, PlainPassword}
import org.fiume.sketch.shared.auth.User.Username
import org.fiume.sketch.shared.auth.algebras.UsersStore
import org.fiume.sketch.shared.common.app.TransactionManager

import java.security.{PrivateKey, PublicKey}
import scala.concurrent.duration.Duration

trait Authenticator[F[_]]:
  def identify(username: Username, password: PlainPassword): F[Either[AuthenticationError, Jwt]]
  def verify(jwt: Jwt): Either[JwtVerificationError, User]

object Authenticator:
  def make[F[_]: Sync, Txn[_]](
    clock: Clock[F],
    store: UsersStore[Txn],
    tx: TransactionManager[F, Txn],
    privateKey: PrivateKey,
    publicKey: PublicKey,
    expirationOffset: Duration
  ): F[Authenticator[F]] = Sync[F].delay {

    new Authenticator[F]:
      override def identify(username: Username, password: PlainPassword): F[Either[AuthenticationError, Jwt]] =
        for
          account <- tx.commit { store.fetchAccount(username) }
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

      override def verify(jwt: Jwt): Either[JwtVerificationError, User] =
        JwtIssuer.verify(jwt, publicKey)
  }
