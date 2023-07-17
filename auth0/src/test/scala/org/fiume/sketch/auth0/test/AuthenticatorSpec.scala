package org.fiume.sketch.auth0

import cats.effect.IO
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.fiume.sketch.auth0.test.EcKeysGens
import org.fiume.sketch.shared.auth0.Model.{Credentials, User, Username}
import org.fiume.sketch.shared.auth0.Passwords.{HashedPassword, PlainPassword, Salt}
import org.fiume.sketch.shared.auth0.test.{PasswordsGens, UserGens}
import org.fiume.sketch.shared.test.EitherSyntax.*
import org.fiume.sketch.shared.test.Gens.DateAndTime.shortDurations
import org.scalacheck.{Gen, ShrinkLowPriority}
import org.scalacheck.effect.PropF.forAllF

import java.security.interfaces.{ECPrivateKey, ECPublicKey}
import scala.concurrent.duration.*

class AuthenticatorSpec
    extends CatsEffectSuite
    with ScalaCheckEffectSuite
    with EcKeysGens
    with PasswordsGens
    with UserGens
    with UsersStoreContext
    with ShrinkLowPriority:

  override def scalaCheckTestParameters =
    super.scalaCheckTestParameters.withMinSuccessfulTests(1)

  test("Authenticator should authenticate a user with valid credentials"):
    forAllF(usersInfo, ecKeyPairs, shortDurations) {
      case ((uuid, username, plainPassword, hashedPassword, salt), (privateKey, _), expirationOffset) =>
        for
          store <- makeUsersStore(Map(uuid -> (username, hashedPassword, salt)))

          authenticator <- Authenticator.make[IO, IO](store, privateKey, expirationOffset)

          result <- authenticator.authenticate(username, plainPassword)
          _ <- IO { assert(result.isRight) }
        yield ()
    }

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

        override def updateUsername(uuid: UUID, user: Username): IO[Unit] =
          storage.update {
            _.updatedWith(uuid) {
              case Some((_, password, salt)) => (user, password, salt).some
              case None                      => none
            }
          }

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
