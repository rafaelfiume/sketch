package org.fiume.sketch.storage.auth0.postgres

import cats.effect.IO
import cats.implicits.*
import doobie.ConnectionIO
import doobie.implicits.*
import doobie.postgres.implicits.*
import munit.ScalaCheckEffectSuite
import org.fiume.sketch.shared.test.Gens
import org.fiume.sketch.storage.auth0.Model.*
import org.fiume.sketch.storage.auth0.Passwords.{HashedPassword, Salt}
import org.fiume.sketch.storage.auth0.algebras.UsersStore
import org.fiume.sketch.storage.auth0.postgres.PostgresUsersStore.*
import org.fiume.sketch.storage.test.support.DockerPostgresSuite
import org.scalacheck.{Arbitrary, Gen, Shrink, ShrinkLowPriority}
import org.scalacheck.effect.PropF.forAllF

import java.time.Instant
import java.util.UUID

class PostgresUsersStoreSpec
    extends ScalaCheckEffectSuite
    with DockerPostgresSuite
    with PostgresUsersStoreSpecContext
    with ShrinkLowPriority:

  test("store and fetch user credentials"):
    forAllF { (user: User, password: HashedPassword, salt: Salt) =>
      will(cleanUsers) {
        PostgresUsersStore.make[IO](transactor()).use { store =>
          for
            uuid <- store.store(user, password, salt).ccommit

            result <- store.fetchCredentials(uuid).ccommit

            _ <- IO {
              assertEquals(result.map(_.user), user.some)
              assertEquals(result.map(_.salt), salt.some)
              assertEquals(result.map(_.password), password.some)
              assertEquals(result.map(_.createdAt), result.map(_.updatedAt))
            }
          yield ()
        }
      }
    }

  test("store user credentials and fetch user"):
    forAllF { (user: User, password: HashedPassword, salt: Salt) =>
      will(cleanUsers) {
        PostgresUsersStore.make[IO](transactor()).use { store =>
          for
            uuid <- store.store(user, password, salt).ccommit

            result <- store.fetchUser(uuid).ccommit

            _ <- IO {
              assertEquals(result, user.some)
            }
          yield ()
        }
      }
    }

  // TODO Check updatedAt is being updated
  test("update user"):
    forAllF { (user: User, password: HashedPassword, salt: Salt, newUser: User) =>
      will(cleanUsers) {
        PostgresUsersStore.make[IO](transactor()).use { store =>
          for
            uuid <- store.store(user, password, salt).ccommit

            _ <- store.updateUser(uuid, newUser).ccommit

            result <- store.fetchUser(uuid).ccommit
            _ <- IO {
              assertEquals(result, newUser.some)
            }
          yield ()
        }
      }
    }

  test("update user password"):
    forAllF { (user: User, password: HashedPassword, salt: Salt, newPassword: HashedPassword) =>
      will(cleanUsers) {
        PostgresUsersStore.make[IO](transactor()).use { store =>
          for
            uuid <- store.store(user, password, salt).ccommit

            _ <- store.updatePassword(uuid, newPassword).ccommit

            result <- store.fetchCredentials(uuid).ccommit
            _ <- IO {
              assertEquals(result.map(_.password), newPassword.some)
            }
          yield ()
        }
      }
    }

  test("delete user"):
    given Arbitrary[(UserCredentials, UserCredentials)] = Arbitrary(
      (for
        fst <- userCredentials
        snd <- userCredentials
      yield (fst, snd)).suchThat { case (fst, snd) => fst.user.username != snd.user.username && fst.user.email != snd.user.email }
    )
    forAllF { (creds: (UserCredentials, UserCredentials)) =>
      will(cleanUsers) {
        PostgresUsersStore.make[IO](transactor()).use { store =>
          val (fst, snd) = creds
          for
            fstUuid <- store.store(fst.user, fst.password, fst.salt).ccommit
            sndUuid <- store.store(snd.user, snd.password, snd.salt).ccommit

            _ <- store.delete(fstUuid).ccommit

            fstResult <- IO.both(
              store.fetchCredentials(fstUuid).ccommit,
              store.fetchUser(fstUuid).ccommit
            )
            sndResult <- IO.both(
              store.fetchCredentials(sndUuid).ccommit,
              store.fetchUser(sndUuid).ccommit
            )
            _ <- IO {
              assertEquals(fstResult._1, none)
              assertEquals(fstResult._2, none)
              assert(sndResult._1.isDefined)
              assert(sndResult._2.isDefined)
            }
          yield ()
        }
      }
    }

  test("set user's `createdAt` and `updatedAt` field to the current timestamp during storage"):
    forAllF { (user: User, password: HashedPassword, salt: Salt) =>
      will(cleanUsers) {
        PostgresUsersStore.make[IO](transactor()).use { store =>
          for
            uuid <- store.store(user, password, salt).ccommit

            createdAt <- store.fetchCreatedAt(uuid).ccommit
            updatedAt <- store.fetchUpdatedAt(uuid).ccommit

            _ <- IO {
              assertEquals(createdAt, updatedAt)
            }
          yield ()
        }
      }
    }

  test("set user's `updatedAt` field to the current timestamp during update"):
    given Arbitrary[Int] = Arbitrary(Gen.choose(1, 2))
    forAllF { (user: User, password: HashedPassword, salt: Salt, newUser: User, newPassword: HashedPassword, condition: Int) =>
      will(cleanUsers) {
        PostgresUsersStore.make[IO](transactor()).use { store =>
          for
            uuid <- store.store(user, password, salt).ccommit

            _ <- condition match
              case 1 => store.updateUser(uuid, newUser).ccommit
              case 2 => store.updatePassword(uuid, newPassword).ccommit

            createdAt <- store.fetchCreatedAt(uuid).ccommit
            updatedAt <- store.fetchUpdatedAt(uuid).ccommit
            _ <- IO {
              assert(
                updatedAt.isAfter(createdAt),
                clue = s"updatedAt=${updatedAt} should be after createdAt=${createdAt}"
              )
            }
          yield ()
        }
      }
    }

trait PostgresUsersStoreSpecContext:
  def cleanUsers: ConnectionIO[Unit] = sql"TRUNCATE TABLE auth.users".update.run.void

  extension (store: PostgresUsersStore[IO])
    def fetchCreatedAt(uuid: UUID): ConnectionIO[Instant] =
      sql"SELECT created_at FROM auth.users WHERE uuid = ${uuid}".query[Instant].unique
    def fetchUpdatedAt(uuid: UUID): ConnectionIO[Instant] =
      sql"SELECT updated_at FROM auth.users WHERE uuid = ${uuid}".query[Instant].unique

  given Arbitrary[User] = Arbitrary(users)
  def users: Gen[User] =
    for
      username <- Gens.Strings.alphaNumString(1, 60).map(Username(_))
      first <- Gens.Strings.alphaNumString(1, 45).map(FirstName(_))
      last <- Gens.Strings.alphaNumString(1, 45).map(LastName(_))
      email <- Gens.Strings.alphaNumString(1, 60).map(Email(_))
    yield User(username, Name(first, last), email)

  // a bcrypt hash approximation for efficience (store assumes correctness)
  given Arbitrary[HashedPassword] = Arbitrary(hashedPassword)
  def hashedPassword: Gen[HashedPassword] =
    Gen.listOfN(60, bcryptBase64Char).map(_.mkString).map(HashedPassword.unsafeFromString)

  // a bcrypt salt approximation for efficience (store assumes correctness)
  given Arbitrary[Salt] = Arbitrary(salts)
  def salts: Gen[Salt] = Gen.listOfN(29, bcryptBase64Char).map(_.mkString).map(Salt.unsafeFromString)

  private def bcryptBase64Char: Gen[Char] = Gen.oneOf(
    Gen.choose('A', 'Z'),
    Gen.choose('a', 'z'),
    Gen.choose('0', '9'),
    Gen.const('.'),
    Gen.const('/')
  )

  def userCredentials: Gen[UserCredentials] =
    for
      id <- Gen.uuid
      password <- hashedPassword
      salt <- salts
      user <- users
      createdAt <- Gens.DateAndTime.dateAndTime
      updatedAt <- Gens.DateAndTime.dateAndTime.suchThat(_.isAfter(createdAt))
    yield UserCredentials(id, password, salt, user, createdAt, updatedAt)
