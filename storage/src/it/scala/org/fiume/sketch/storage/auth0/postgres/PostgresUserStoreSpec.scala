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
import org.fiume.sketch.storage.auth0.algebras.UserStore
import org.fiume.sketch.storage.auth0.postgres.PostgresUserStore.*
import org.fiume.sketch.storage.test.support.DockerPostgresSuite
import org.scalacheck.{Arbitrary, Gen, Shrink, ShrinkLowPriority}
import org.scalacheck.effect.PropF.forAllF

class PostgresUserStoreSpec
    extends ScalaCheckEffectSuite
    with DockerPostgresSuite
    with PostgresUserStoreSpecContext
    with ShrinkLowPriority:

  test("store and fetch user credentials"):
    forAllF { (user: User, password: HashedPassword, salt: Salt) =>
      will(cleanUsers) {
        PostgresUserStore.make[IO](transactor()).use { store =>
          for
            storedCredentials <- store.store(user, password, salt).ccommit

            result <- store.fetchCredentials(user.username).ccommit

            _ <- IO {
              assertEquals(result, storedCredentials.some)
              assertEquals(storedCredentials.user, user)
              assertEquals(storedCredentials.salt, salt)
              assertEquals(storedCredentials.password, password)
              assertEquals(storedCredentials.createdAt, storedCredentials.updatedAt)
            }
          yield ()
        }
      }
    }

  test("store and fetch user"):
    forAllF { (user: User, password: HashedPassword, salt: Salt) =>
      will(cleanUsers) {
        PostgresUserStore.make[IO](transactor()).use { store =>
          for
            _ <- store.store(user, password, salt).ccommit

            result <- store.fetchUser(user.username).ccommit

            _ <- IO {
              assertEquals(result, user.some)
            }
          yield ()
        }
      }
    }

  // TODO Check updatedAt is being updated
  test("update user"):
    forAllF { (user: User, password: HashedPassword, salt: Salt, up: User) =>
      will(cleanUsers) {
        PostgresUserStore.make[IO](transactor()).use { store =>
          for
            _ <- store.store(user, password, salt).ccommit

            updatedUser = up.copy(username = user.username)
            _ <- store.updateUser(updatedUser).ccommit

            result <- store.fetchCredentials(updatedUser.username).ccommit
            _ <- IO {
              assertEquals(result.map(_.user), updatedUser.some)
              assert(
                result.exists(creds => creds.updatedAt.isAfter(creds.createdAt)),
                clue = s"updatedAt=${result.map(_.updatedAt)} should be after createdAt=${result.map(_.createdAt)}"
              )
            }
          yield ()
        }
      }
    }

  test("update password"):
    forAllF { (user: User, password: HashedPassword, salt: Salt, newPassword: HashedPassword) =>
      will(cleanUsers) {
        PostgresUserStore.make[IO](transactor()).use { store =>
          for
            _ <- store.store(user, password, salt).ccommit

            _ <- store.updatePassword(user.username, newPassword).ccommit

            result <- store.fetchCredentials(user.username).ccommit
            _ <- IO {
              assertEquals(result.map(_.password), newPassword.some)
              assert(
                result.exists(creds => creds.updatedAt.isAfter(creds.createdAt)),
                clue = s"updatedAt=${result.map(_.updatedAt)} should be after createdAt=${result.map(_.createdAt)}"
              )
            }
          yield ()
        }
      }
    }

  test("remove user then fetch returns none"):
    forAllF { (user: User, password: HashedPassword, salt: Salt) =>
      will(cleanUsers) {
        PostgresUserStore.make[IO](transactor()).use { store =>
          for
            _ <- store.store(user, password, salt).ccommit

            _ <- store.remove(user.username).ccommit

            result <- IO.both(
              store.fetchCredentials(user.username).ccommit,
              store.fetchUser(user.username).ccommit
            )
            _ <- IO {
              assertEquals(result._1, none)
              assertEquals(result._2, none)
            }
          yield ()
        }
      }
    }

trait PostgresUserStoreSpecContext:
  def cleanUsers: ConnectionIO[Unit] = sql"TRUNCATE TABLE users".update.run.void

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
