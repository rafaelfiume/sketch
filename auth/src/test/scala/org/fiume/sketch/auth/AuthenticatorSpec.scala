package org.fiume.sketch.auth

import cats.effect.IO
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.fiume.sketch.auth.testkit.EcKeysGens
import org.fiume.sketch.shared.auth.domain.{Account, AccountState, Jwt, User}
import org.fiume.sketch.shared.auth.domain.AccountState.SoftDeleted
import org.fiume.sketch.shared.auth.domain.AuthenticationError.*
import org.fiume.sketch.shared.auth.domain.JwtError.*
import org.fiume.sketch.shared.auth.domain.Passwords.PlainPassword
import org.fiume.sketch.shared.auth.domain.User.Username
import org.fiume.sketch.shared.auth.testkit.{UserGens, UsersStoreContext}
import org.fiume.sketch.shared.auth.testkit.UserGens.*
import org.fiume.sketch.shared.testkit.ClockContext
import org.fiume.sketch.shared.testkit.Gens.DateAndTime.shortDurations
import org.fiume.sketch.shared.testkit.syntax.EitherSyntax.*
import org.fiume.sketch.shared.testkit.syntax.StringSyntax.*
import org.scalacheck.ShrinkLowPriority
import org.scalacheck.effect.PropF.forAllF

import java.security.Security
import java.time.Instant
import scala.concurrent.duration.*

class AuthenticatorSpec
    extends CatsEffectSuite
    with ScalaCheckEffectSuite
    with EcKeysGens
    with ClockContext
    with UsersStoreContext
    with AuthenticatorSpecContext
    with ShrinkLowPriority:

  override def scalaCheckTestParameters = super.scalaCheckTestParameters.withMinSuccessfulTests(1)

  Security.addProvider(new BouncyCastleProvider())

  test("valid username and password authentication results in a jwt representing the user"):
    forAllF(validCredentialsWithIdAndPlainPassword, ecKeyPairs, shortDurations) {
      case ((credentials, plainPassword), (privateKey, publicKey), expirationOffset) =>
        for
          store <- makeUsersStore(credentials)

          authenticator <- Authenticator.make[IO, IO](makeFrozenClock(), store, privateKey, publicKey, expirationOffset)
          result <- authenticator.authenticate(credentials.username, plainPassword)

          user = authenticator.verify(result.rightOrFail)
        yield assertEquals(user.rightOrFail, User(credentials.uuid, credentials.username))
    }

  test("wrong password authentication fails"):
    forAllF(validCredentialsWithIdAndPlainPassword, ecKeyPairs, shortDurations) {
      case ((credentials, plainPassword), (privateKey, publicKey), expirationOffset) =>
        for
          store <- makeUsersStore(credentials)
          authenticator <- Authenticator.make[IO, IO](makeFrozenClock(), store, privateKey, publicKey, expirationOffset)

          result <- authenticator.authenticate(credentials.username, plainPassword.shuffled)
//
        yield assertEquals(result.leftOrFail, InvalidPasswordError)
    }

  test("unknown username authentication fails"):
    forAllF(validCredentialsWithIdAndPlainPassword, ecKeyPairs, shortDurations) {
      case ((credentials, plainPassword), (privateKey, publicKey), expirationOffset) =>
        for
          store <- makeUsersStore(credentials)

          authenticator <- Authenticator.make[IO, IO](makeFrozenClock(), store, privateKey, publicKey, expirationOffset)
          result <- authenticator.authenticate(credentials.username.shuffled, plainPassword)
//
        yield assertEquals(result.leftOrFail, UserNotFoundError)
    }

  test("inactive account authentication fails"):
    forAllF(validCredentialsWithIdAndPlainPassword, ecKeyPairs, shortDurations) {
      case ((credentials, plainPassword), (privateKey, publicKey), expirationOffset) =>
        val deletedAt = Instant.now()
        // The idea is to check all possible inactive states within the AccountState ADT here
        val userAccount = Account(credentials.uuid, credentials, state = SoftDeleted(deletedAt))
        for
          store <- makeUsersStoreForAccount(userAccount)

          authenticator <- Authenticator.make[IO, IO](makeFrozenClock(), store, privateKey, publicKey, expirationOffset)
          result <- authenticator.authenticate(credentials.username, plainPassword)
//
        yield assertEquals(result.leftOrFail, AccountNotActiveError)
    }

  test("expired token verification fails"):
    forAllF(validCredentialsWithIdAndPlainPassword, ecKeyPairs, shortDurations) {
      case ((credentials, plainPassword), (privateKey, publicKey), expirationOffset) =>
        val frozenClock = makeFrozenClock(Instant.now().minusSeconds(expirationOffset.toSeconds))
        for
          store <- makeUsersStore(credentials)
          authenticator <- Authenticator.make[IO, IO](frozenClock, store, privateKey, publicKey, expirationOffset)
          jwt <- authenticator.authenticate(credentials.username, plainPassword).map(_.rightOrFail)

          result = authenticator.verify(jwt)
//
        yield
          assert(result.leftOrFail.isInstanceOf[JwtExpirationError])
          assert(result.leftOrFail.details.startsWith("The token is expired since"))
    }

  test("tampered token verification fails"):
    forAllF(validCredentialsWithIdAndPlainPassword, ecKeyPairs, shortDurations) {
      case ((credentials, plainPassword), (privateKey, publicKey), expirationOffset) =>
        for
          store <- makeUsersStore(credentials)
          authenticator <- Authenticator.make[IO, IO](makeFrozenClock(), store, privateKey, publicKey, expirationOffset)
          jwt <- authenticator.authenticate(credentials.username, plainPassword).map(_.rightOrFail)

          result = authenticator.verify(jwt.tampered)
//
        yield assertEquals(
          result.leftOrFail,
          JwtEmptySignatureError("No signature found inside the token while trying to verify it with a key.")
        )
    }

  test("invalid token verification fails"):
    forAllF(validCredentialsWithIdAndPlainPassword, ecKeyPairs, shortDurations) {
      case ((credentials, plainPassword), (privateKey, publicKey), expirationOffset) =>
        for
          store <- makeUsersStore(credentials)
          authenticator <- Authenticator.make[IO, IO](makeFrozenClock(), store, privateKey, publicKey, expirationOffset)
          jwt <- authenticator.authenticate(credentials.username, plainPassword).map(_.rightOrFail)

          result = authenticator.verify(jwt.reversed)
//
        yield
          assert(result.leftOrFail.isInstanceOf[JwtInvalidTokenError])
          assert(result.leftOrFail.details.startsWith("Invalid Jwt:"), clue = s"actual: ${result.leftOrFail.details}")
    }

  test("token verification with invalid public key fails"):
    forAllF(validCredentialsWithIdAndPlainPassword, ecKeyPairs, ecKeyPairs, shortDurations) {
      case ((credentials, plainPassword), (privateKey, _), (_, strangePublicKey), expirationOffset) =>
        for
          store <- makeUsersStore(credentials)
          authenticator <- Authenticator.make[IO, IO](makeFrozenClock(), store, privateKey, strangePublicKey, expirationOffset)
          jwt <- authenticator.authenticate(credentials.username, plainPassword).map(_.rightOrFail)

          result = authenticator.verify(jwt)
//
        yield assertEquals(
          result.leftOrFail,
          JwtValidationError("Invalid signature for this token or wrong algorithm.")
        )
    }

trait AuthenticatorSpecContext:
  extension (plainPassword: PlainPassword)
    def shuffled: PlainPassword = PlainPassword.makeUnsafeFromString(plainPassword.value._shuffled)

  extension (username: Username) def shuffled: Username = Username.makeUnsafeFromString(username.value._shuffled)

  extension (jwt: Jwt)
    def reversed: Jwt = Jwt.makeUnsafeFromString(jwt.value._reversed)
    def tampered: Jwt = Jwt.makeUnsafeFromString(jwt.value.split('.').dropRight(1).mkString("."))
