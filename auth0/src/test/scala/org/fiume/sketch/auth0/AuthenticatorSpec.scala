package org.fiume.sketch.auth0

import cats.effect.{Clock, IO}
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.fiume.sketch.auth0.AuthenticationError.*
import org.fiume.sketch.auth0.Authenticator.*
import org.fiume.sketch.auth0.JwtError.*
import org.fiume.sketch.auth0.testkit.EcKeysGens
import org.fiume.sketch.shared.auth0.Passwords.PlainPassword
import org.fiume.sketch.shared.auth0.User
import org.fiume.sketch.shared.auth0.User.Username
import org.fiume.sketch.shared.auth0.testkit.{UserGens, UsersStoreContext}
import org.fiume.sketch.shared.auth0.testkit.UserGens.*
import org.fiume.sketch.shared.testkit.ClockContext
import org.fiume.sketch.shared.testkit.EitherSyntax.*
import org.fiume.sketch.shared.testkit.Gens.DateAndTime.shortDurations
import org.fiume.sketch.shared.testkit.StringSyntax.*
import org.scalacheck.ShrinkLowPriority
import org.scalacheck.effect.PropF.forAllF

import java.security.Security
import java.time.ZonedDateTime
import scala.concurrent.duration.*

class AuthenticatorSpec
    extends CatsEffectSuite
    with ScalaCheckEffectSuite
    with EcKeysGens
    with ClockContext
    with UsersStoreContext
    with AuthenticatorSpecContext
    with ShrinkLowPriority:

  override def scalaCheckTestParameters =
    super.scalaCheckTestParameters.withMinSuccessfulTests(1)

  Security.addProvider(new BouncyCastleProvider())

  test("authenticate and verify user with valid credentials"):
    forAllF(validCredentialsWithIdAndPlainPassword, ecKeyPairs, shortDurations) {
      case ((credentials, plainPassword), (privateKey, publicKey), expirationOffset) =>
        for
          store <- makeUsersStore(credentials)

          authenticator <- Authenticator.make[IO, IO](store, privateKey, publicKey, expirationOffset)
          result <- authenticator.authenticate(credentials.username, plainPassword).map(_.rightValue)

          user = authenticator.verify(result)
          _ <- IO { assertEquals(user.rightValue, User(credentials.uuid, credentials.username)) }
        yield ()
    }

  test("do not authenticate a user with wrong password"):
    forAllF(validCredentialsWithIdAndPlainPassword, ecKeyPairs, shortDurations) {
      case ((credentials, plainPassword), (privateKey, publicKey), expirationOffset) =>
        for
          store <- makeUsersStore(credentials)

          authenticator <- Authenticator.make[IO, IO](store, privateKey, publicKey, expirationOffset)

          result <- authenticator.authenticate(credentials.username, plainPassword.shuffled)
          _ <- IO { assertEquals(result.leftValue, InvalidPasswordError) }
        yield ()
    }

  test("do not not authenticate a user with unknown username"):
    forAllF(validCredentialsWithIdAndPlainPassword, ecKeyPairs, shortDurations) {
      case ((credentials, plainPassword), (privateKey, publicKey), expirationOffset) =>
        for
          store <- makeUsersStore(credentials)

          authenticator <- Authenticator.make[IO, IO](store, privateKey, publicKey, expirationOffset)
          result <- authenticator.authenticate(credentials.username.shuffled, plainPassword)

          _ <- IO { assertEquals(result.leftValue, UserNotFoundError) }
        yield ()
    }

  test("verify expired token"):
    forAllF(validCredentialsWithIdAndPlainPassword, ecKeyPairs, shortDurations) {
      case ((credentials, plainPassword), (privateKey, publicKey), expirationOffset) =>
        given Clock[IO] = makeFrozenTime(ZonedDateTime.now().minusSeconds(expirationOffset.toSeconds))
        for
          store <- makeUsersStore(credentials)
          authenticator <- Authenticator.make[IO, IO](store, privateKey, publicKey, expirationOffset)
          jwtToken <- authenticator.authenticate(credentials.username, plainPassword).map(_.rightValue)

          result = authenticator.verify(jwtToken)

          _ <- IO { assert(result.leftValue.isInstanceOf[JwtExpirationError]) }
          _ <- IO { assert(result.leftValue.details.startsWith("The token is expired since")) }
        yield ()
    }

  test("verify tampered token"):
    forAllF(validCredentialsWithIdAndPlainPassword, ecKeyPairs, shortDurations) {
      case ((credentials, plainPassword), (privateKey, publicKey), expirationOffset) =>
        for
          store <- makeUsersStore(credentials)
          authenticator <- Authenticator.make[IO, IO](store, privateKey, publicKey, expirationOffset)
          token <- authenticator.authenticate(credentials.username, plainPassword).map(_.rightValue)

          result = authenticator.verify(token.tampered)

          _ <- IO {
            assertEquals(result.leftValue,
                         JwtEmptySignatureError("No signature found inside the token while trying to verify it with a key.")
            )
          }
        yield ()
    }

  test("verify invalid token"):
    forAllF(validCredentialsWithIdAndPlainPassword, ecKeyPairs, shortDurations) {
      case ((credentials, plainPassword), (privateKey, publicKey), expirationOffset) =>
        for
          store <- makeUsersStore(credentials)
          authenticator <- Authenticator.make[IO, IO](store, privateKey, publicKey, expirationOffset)
          jwtToken <- authenticator.authenticate(credentials.username, plainPassword).map(_.rightValue)

          result = authenticator.verify(jwtToken.reversed)

          _ <- IO { assert(result.leftValue.isInstanceOf[JwtInvalidTokenError]) }
          _ <- IO { assert(result.leftValue.details.startsWith("Invalid Jwt token:")) }
        yield ()
    }

  test("verify fails with invalid public key"):
    forAllF(validCredentialsWithIdAndPlainPassword, ecKeyPairs, ecKeyPairs, shortDurations) {
      case ((credentials, plainPassword), (privateKey, _), (_, strangePublicKey), expirationOffset) =>
        for
          store <- makeUsersStore(credentials)
          authenticator <- Authenticator.make[IO, IO](store, privateKey, strangePublicKey, expirationOffset)
          jwtToken <- authenticator.authenticate(credentials.username, plainPassword).map(_.rightValue)

          result = authenticator.verify(jwtToken)

          _ <- IO { assertEquals(result.leftValue, JwtValidationError("Invalid signature for this token or wrong algorithm.")) }
        yield ()
    }

trait AuthenticatorSpecContext:
  extension (plainPassword: PlainPassword)
    def shuffled: PlainPassword = PlainPassword.notValidatedFromString(plainPassword.value._shuffled)

  extension (username: Username) def shuffled: Username = Username.notValidatedFromString(username.value._shuffled)

  extension (token: JwtToken)
    def reversed: JwtToken = JwtToken.notValidatedFromString(token.value._reversed)
    def tampered: JwtToken = JwtToken.notValidatedFromString(token.value.split('.').dropRight(1).mkString("."))
