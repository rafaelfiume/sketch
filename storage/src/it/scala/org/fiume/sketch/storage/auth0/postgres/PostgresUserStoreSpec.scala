package org.fiume.sketch.storage.auth0.postgres

import cats.effect.IO
import cats.implicits.*
import doobie.ConnectionIO
import doobie.implicits.*
import doobie.postgres.implicits.*
import munit.ScalaCheckEffectSuite
import org.fiume.sketch.shared.test.Gens
import org.fiume.sketch.storage.auth0.Model.*
import org.fiume.sketch.storage.auth0.algebras.UserStore
import org.fiume.sketch.storage.auth0.postgres.PostgresUserStore.*
import org.fiume.sketch.storage.test.support.DockerPostgresSuite
import org.scalacheck.{Gen, Shrink}
import org.scalacheck.effect.PropF.forAllF

class PostgresUserStoreSpec extends ScalaCheckEffectSuite with DockerPostgresSuite with PostgresUserStoreSpecContext:

  // shrinking just make failing tests messages more obscure
  given noShrink[T]: Shrink[T] = Shrink.shrinkAny

  test("store and fetch user credentials") {
    forAllF(users, passwords, salts) { (user, password, salt) =>
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
  }

  test("store and fetch user") {
    forAllF(users, passwords, salts) { (user, password, salt) =>
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
  }

  test("update user") {
    forAllF(users, passwords, salts, users) { (user, password, salt, up) =>
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
                result.exists(user => user.updatedAt.isAfter(user.createdAt)),
                clue = "updatedAt should be after createdAt"
              )
            }
          yield ()
        }
      }
    }
  }

  test("update password") {
    forAllF(users, passwords, salts, passwords) { (user, password, salt, newPassword) =>
      will(cleanUsers) {
        PostgresUserStore.make[IO](transactor()).use { store =>
          for
            _ <- store.store(user, password, salt).ccommit

            _ <- store.updatePassword(user.username, newPassword).ccommit

            result <- store.fetchCredentials(user.username).ccommit
            _ <- IO {
              assertEquals(result.map(_.password), newPassword.some)
              assert(
                result.exists(user => user.updatedAt.isAfter(user.createdAt)),
                clue = "updatedAt should be after createdAt"
              )
            }
          yield ()
        }
      }
    }
  }

  test("remove user then fetch returns none") {
    forAllF(users, passwords, salts) { (user, password, salt) =>
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
  }

trait PostgresUserStoreSpecContext:
  def cleanUsers: ConnectionIO[Unit] = sql"TRUNCATE TABLE users".update.run.void

  def users: Gen[User] =
    for
      username <- Gens.Strings.alphaNumString(1, 50).map(Username(_))
      first <- Gens.Strings.alphaNumString(1, 50).map(FirstName(_))
      last <- Gens.Strings.alphaNumString(1, 50).map(LastName(_))
      email <- Gens.Strings.alphaNumString(1, 50).map(Email(_))
    yield User(username, Name(first, last), email)

  def passwords: Gen[PasswordHash] = Gens.Strings.alphaNumString(1, 50).map(PasswordHash(_))

  def salts: Gen[Salt] = Gens.Strings.alphaNumString(1, 50).map(Salt(_))

  def userCredentials: Gen[UserCredentials] =
    for
      id <- Gen.uuid
      password <- passwords
      salt <- salts
      user <- users
      createdAt <- Gens.DateAndTime.dateAndTime
      updatedAt <- Gens.DateAndTime.dateAndTime.suchThat(_.isAfter(createdAt))
    yield UserCredentials(id, password, salt, user, createdAt, updatedAt)
