package org.fiume.sketch.auth0

import cats.effect.{Clock, IO}
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.fiume.sketch.auth0.Authenticator.*
import org.fiume.sketch.auth0.test.EcKeysGens
import org.fiume.sketch.shared.auth0.Passwords.{HashedPassword, PlainPassword, Salt}
import org.fiume.sketch.shared.auth0.User
import org.fiume.sketch.shared.auth0.User.{Credentials, Username}
import org.fiume.sketch.shared.auth0.test.{PasswordsGens, UserGens}
import org.fiume.sketch.shared.test.ClockContext
import org.fiume.sketch.shared.test.EitherSyntax.*
import org.fiume.sketch.shared.test.Gens.DateAndTime.shortDurations
import org.fiume.sketch.shared.test.StringSyntax.*
import org.scalacheck.{Gen, ShrinkLowPriority}
import org.scalacheck.effect.PropF.forAllF

import java.security.interfaces.{ECPrivateKey, ECPublicKey}
import java.time.ZonedDateTime
import scala.concurrent.duration.*

class AuthenticatorSpec
    extends CatsEffectSuite
    with ScalaCheckEffectSuite
    with EcKeysGens
    with PasswordsGens
    with UserGens
    with ClockContext
    with UsersStoreContext
    with AuthenticatorSpecContext
    with ShrinkLowPriority:

  override def scalaCheckTestParameters =
    super.scalaCheckTestParameters.withMinSuccessfulTests(1)

  test("authenticate and verify user with valid credentials"):
    forAllF(usersAuthenticationInfo, ecKeyPairs, shortDurations) {
      case ((uuid, username, plainPassword, hashedPassword, salt), (privateKey, publicKey), expirationOffset) =>
        for
          store <- makeUsersStore(Map(uuid -> (username, hashedPassword, salt)))

          authenticator <- Authenticator.make[IO, IO](store, privateKey, publicKey, expirationOffset)
          result <- authenticator.authenticate(username, plainPassword).map(_.rightValue)

          user = authenticator.verify(result)
          _ <- IO { assertEquals(user.rightValue, User(uuid, username)) }
        yield ()
    }

  test("do not authenticate a user with wrong password"):
    forAllF(usersAuthenticationInfo, ecKeyPairs, shortDurations) {
      case ((uuid, username, plainPassword, hashedPassword, salt), (privateKey, publicKey), expirationOffset) =>
        for
          store <- makeUsersStore(Map(uuid -> (username, hashedPassword, salt)))

          authenticator <- Authenticator.make[IO, IO](store, privateKey, publicKey, expirationOffset)

          result <- authenticator.authenticate(username, plainPassword.shuffled)
          _ <- IO { assertEquals(result.leftValue, InvalidPasswordError) }
        yield ()
    }

  test("do not not authenticate a user with unknown username"):
    forAllF(usersAuthenticationInfo, ecKeyPairs, shortDurations) {
      case ((uuid, username, plainPassword, hashedPassword, salt), (privateKey, publicKey), expirationOffset) =>
        for
          store <- makeUsersStore(Map(uuid -> (username, hashedPassword, salt)))

          authenticator <- Authenticator.make[IO, IO](store, privateKey, publicKey, expirationOffset)
          result <- authenticator.authenticate(username.shuffled, plainPassword)

          _ <- IO { assertEquals(result.leftValue, UserNotFoundError) }
        yield ()
    }

  test("verify expired token"):
    forAllF(usersAuthenticationInfo, ecKeyPairs, shortDurations) {
      case ((uuid, username, plainPassword, hashedPassword, salt), (privateKey, publicKey), expirationOffset) =>
        given Clock[IO] = makeFrozenTime(ZonedDateTime.now().minusSeconds(expirationOffset.toSeconds))
        for
          store <- makeUsersStore(Map(uuid -> (username, hashedPassword, salt)))
          authenticator <- Authenticator.make[IO, IO](store, privateKey, publicKey, expirationOffset)
          jwtToken <- authenticator.authenticate(username, plainPassword).map(_.rightValue)

          result = authenticator.verify(jwtToken)

          _ <- IO { assert(result.leftValue.isInstanceOf[JwtExpirationError]) }
          _ <- IO { assert(result.leftValue.details.startsWith("The token is expired since")) }
        yield ()
    }

  test("verify tampered token"):
    forAllF(usersAuthenticationInfo, ecKeyPairs, shortDurations) {
      case ((uuid, username, plainPassword, hashedPassword, salt), (privateKey, publicKey), expirationOffset) =>
        for
          store <- makeUsersStore(Map(uuid -> (username, hashedPassword, salt)))
          authenticator <- Authenticator.make[IO, IO](store, privateKey, publicKey, expirationOffset)
          token <- authenticator.authenticate(username, plainPassword).map(_.rightValue)

          result = authenticator.verify(token.tampered)

          _ <- IO {
            assertEquals(result.leftValue,
                         JwtEmptySignatureError("No signature found inside the token while trying to verify it with a key.")
            )
          }
        yield ()
    }

  test("verify invalid token"):
    forAllF(usersAuthenticationInfo, ecKeyPairs, shortDurations) {
      case ((uuid, username, plainPassword, hashedPassword, salt), (privateKey, publicKey), expirationOffset) =>
        for
          store <- makeUsersStore(Map(uuid -> (username, hashedPassword, salt)))
          authenticator <- Authenticator.make[IO, IO](store, privateKey, publicKey, expirationOffset)
          jwtToken <- authenticator.authenticate(username, plainPassword).map(_.rightValue)

          result = authenticator.verify(jwtToken.reversed)

          _ <- IO { assert(result.leftValue.isInstanceOf[JwtInvalidTokenError]) }
          _ <- IO { assert(result.leftValue.details.startsWith("Invalid Jwt token:")) }
        yield ()
    }

  test("verify fails with invalid public key"):
    forAllF(usersAuthenticationInfo, ecKeyPairs, ecKeyPairs, shortDurations) {
      case ((uuid, username, plainPassword, hashedPassword, salt), (privateKey, _), (_, strangePublicKey), expirationOffset) =>
        for
          store <- makeUsersStore(Map(uuid -> (username, hashedPassword, salt)))
          authenticator <- Authenticator.make[IO, IO](store, privateKey, strangePublicKey, expirationOffset)
          jwtToken <- authenticator.authenticate(username, plainPassword).map(_.rightValue)

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

trait UsersStoreContext:
  import cats.effect.Ref
  import cats.implicits.*
  import org.fiume.sketch.shared.auth0.algebras.UsersStore
  import java.util.UUID
  import org.fiume.sketch.shared.auth0.Passwords.Salt

  def makeUsersStore(): IO[UsersStore[IO, IO]] = makeUsersStore(state = Map.empty)

  def makeUsersStore(state: Map[UUID, (Username, HashedPassword, Salt)]): IO[UsersStore[IO, IO]] =
    Ref.of[IO, Map[UUID, (Username, HashedPassword, Salt)]](state).map { storage =>
      new UsersStore[IO, IO]:
        override def store(username: Username, password: HashedPassword, salt: Salt): IO[UUID] =
          IO.randomUUID.flatMap { uuid =>
            storage
              .update {
                _.updated(uuid, (username, password, salt))
              }
              .as(uuid)
          }

        override def fetchUser(uuid: UUID): IO[Option[User]] =
          storage.get.map(_.collectFirst {
            case (storedUuid, (username, _, _)) if storedUuid == uuid =>
              User(uuid, username)
          })

        override def fetchCredentials(username: Username): IO[Option[Credentials]] =
          storage.get.map(_.collectFirst {
            case (uuid, (storedUsername, hashedPassword, salt)) if storedUsername == username =>
              Credentials(uuid, storedUsername, hashedPassword)
          })

        override def updatePassword(uuid: UUID, password: HashedPassword): IO[Unit] =
          storage.update {
            _.updatedWith(uuid) {
              case Some((username, _, salt)) => (username, password, salt).some
              case None                      => none
            }
          }

        override def delete(uuid: UUID): IO[Unit] =
          storage.update(_.removed(uuid))

        val commit: [A] => IO[A] => IO[A] = [A] => (action: IO[A]) => action

        val lift: [A] => IO[A] => IO[A] = [A] => (action: IO[A]) => action
    }
