package org.fiume.sketch.auth0

import cats.effect.{Clock, IO}
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.fiume.sketch.auth0.AuthenticationError.*
import org.fiume.sketch.auth0.JwtError.*
import org.fiume.sketch.auth0.testkit.EcKeysGens
import org.fiume.sketch.shared.auth0.Passwords.PlainPassword
import org.fiume.sketch.shared.auth0.User
import org.fiume.sketch.shared.auth0.User.Username
import org.fiume.sketch.shared.auth0.testkit.{UserGens, UsersStoreContext}
import org.fiume.sketch.shared.auth0.testkit.UserGens.*
import org.fiume.sketch.shared.testkit.ClockContext
import org.fiume.sketch.shared.testkit.Gens.DateAndTime.shortDurations
import org.fiume.sketch.shared.testkit.Syntax.EitherSyntax.*
import org.fiume.sketch.shared.testkit.Syntax.StringSyntax.*
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

  override def scalaCheckTestParameters = super.scalaCheckTestParameters.withMinSuccessfulTests(1)

  Security.addProvider(new BouncyCastleProvider())

  test("valid username and password authentication results in a jwt token representing the user"):
    forAllF(validCredentialsWithIdAndPlainPassword, ecKeyPairs, shortDurations) {
      case ((credentials, plainPassword), (privateKey, publicKey), expirationOffset) =>
        for
          store <- makeUsersStore(credentials)

          authenticator <- Authenticator.make[IO, IO](store, privateKey, publicKey, expirationOffset)
          result <- authenticator.authenticate(credentials.username, plainPassword).map(_.rightValue)

          user = authenticator.verify(result)
        yield assertEquals(user.rightValue, User(credentials.uuid, credentials.username))
    }

  test("wrong password authentication fails"):
    forAllF(validCredentialsWithIdAndPlainPassword, ecKeyPairs, shortDurations) {
      case ((credentials, plainPassword), (privateKey, publicKey), expirationOffset) =>
        for
          store <- makeUsersStore(credentials)

          authenticator <- Authenticator.make[IO, IO](store, privateKey, publicKey, expirationOffset)

          result <- authenticator.authenticate(credentials.username, plainPassword.shuffled)
        yield assertEquals(result.leftValue, InvalidPasswordError)
    }

  test("unknown username authentication fails"):
    forAllF(validCredentialsWithIdAndPlainPassword, ecKeyPairs, shortDurations) {
      case ((credentials, plainPassword), (privateKey, publicKey), expirationOffset) =>
        for
          store <- makeUsersStore(credentials)

          authenticator <- Authenticator.make[IO, IO](store, privateKey, publicKey, expirationOffset)
          result <- authenticator.authenticate(credentials.username.shuffled, plainPassword)
//
        yield assertEquals(result.leftValue, UserNotFoundError)
    }

  test("expired token verification fails"):
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

  test("tampered token verification fails"):
    forAllF(validCredentialsWithIdAndPlainPassword, ecKeyPairs, shortDurations) {
      case ((credentials, plainPassword), (privateKey, publicKey), expirationOffset) =>
        for
          store <- makeUsersStore(credentials)
          authenticator <- Authenticator.make[IO, IO](store, privateKey, publicKey, expirationOffset)
          token <- authenticator.authenticate(credentials.username, plainPassword).map(_.rightValue)

          result = authenticator.verify(token.tampered)
//
        yield assertEquals(
          result.leftValue,
          JwtEmptySignatureError("No signature found inside the token while trying to verify it with a key.")
        )
    }

  test("invalid token verification fails"):
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

  test("token verification with invalid public key fails"):
    forAllF(validCredentialsWithIdAndPlainPassword, ecKeyPairs, ecKeyPairs, shortDurations) {
      case ((credentials, plainPassword), (privateKey, _), (_, strangePublicKey), expirationOffset) =>
        for
          store <- makeUsersStore(credentials)
          authenticator <- Authenticator.make[IO, IO](store, privateKey, strangePublicKey, expirationOffset)
          jwtToken <- authenticator.authenticate(credentials.username, plainPassword).map(_.rightValue)

          result = authenticator.verify(jwtToken)
//
        yield assertEquals(
          result.leftValue,
          JwtValidationError("Invalid signature for this token or wrong algorithm.")
        )
    }

trait AuthenticatorSpecContext:
  extension (plainPassword: PlainPassword)
    def shuffled: PlainPassword = PlainPassword.notValidatedFromString(plainPassword.value._shuffled)

  extension (username: Username) def shuffled: Username = Username.notValidatedFromString(username.value._shuffled)

  extension (token: JwtToken)
    def reversed: JwtToken = JwtToken.notValidatedFromString(token.value._reversed)
    def tampered: JwtToken = JwtToken.notValidatedFromString(token.value.split('.').dropRight(1).mkString("."))
