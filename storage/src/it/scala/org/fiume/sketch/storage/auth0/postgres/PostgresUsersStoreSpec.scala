package org.fiume.sketch.storage.auth0.postgres

import cats.effect.IO
import cats.implicits.*
import doobie.ConnectionIO
import doobie.implicits.*
import doobie.postgres.implicits.*
import munit.ScalaCheckEffectSuite
import org.fiume.sketch.shared.auth0.Model.*
import org.fiume.sketch.shared.auth0.Passwords
import org.fiume.sketch.shared.auth0.Passwords.{HashedPassword, Salt}
import org.fiume.sketch.shared.auth0.algebras.UsersStore
import org.fiume.sketch.shared.auth0.support.{PasswordGens, UserGens}
import org.fiume.sketch.shared.test.Gens
import org.fiume.sketch.storage.auth0.postgres.DoobieMappings.given
import org.fiume.sketch.storage.auth0.postgres.PostgresUsersStore.*
import org.fiume.sketch.storage.test.support.DockerPostgresSuite
import org.scalacheck.{Arbitrary, Gen, Shrink, ShrinkLowPriority}
import org.scalacheck.effect.PropF.forAllF

import java.time.Instant
import java.util.UUID

class PostgresUsersStoreSpec
    extends ScalaCheckEffectSuite
    with DockerPostgresSuite
    with PasswordGens
    with UserGens
    with PostgresUsersStoreSpecContext
    with ShrinkLowPriority:

  test("store user credentials and fetch user"):
    forAllF { (username: Username, password: HashedPassword, salt: Salt) =>
      will(cleanUsers) {
        PostgresUsersStore.make[IO](transactor()).use { store =>
          for
            uuid <- store.store(username, password, salt).ccommit

            result <- store.fetchUser(uuid).ccommit

            _ <- IO {
              assertEquals(result, User(uuid, username).some)
            }
          yield ()
        }
      }
    }

  test("update user"):
    forAllF { (username: Username, password: HashedPassword, salt: Salt, newUsername: Username) =>
      will(cleanUsers) {
        PostgresUsersStore.make[IO](transactor()).use { store =>
          for
            uuid <- store.store(username, password, salt).ccommit

            _ <- store.updateUsername(uuid, newUsername).ccommit

            result <- store.fetchUser(uuid).ccommit
            _ <- IO {
              assertEquals(result, User(uuid, newUsername).some)
            }
          yield ()
        }
      }
    }

  test("update user password"):
    forAllF { (username: Username, password: HashedPassword, salt: Salt, newPassword: HashedPassword) =>
      will(cleanUsers) {
        PostgresUsersStore.make[IO](transactor()).use { store =>
          for
            uuid <- store.store(username, password, salt).ccommit

            _ <- store.updatePassword(uuid, newPassword).ccommit

            result <- store.fetchPassword(uuid).ccommit
            _ <- IO {
              assertEquals(result, newPassword)
            }
          yield ()
        }
      }
    }

  test("delete user"):
    given Arbitrary[(Username, Username)] = Arbitrary(
      (for
        fst <- usernames
        snd <- usernames
      yield (fst, snd)).suchThat { case (fst, snd) => fst != snd }
    )
    forAllF { (users: (Username, Username), fstPass: HashedPassword, fstSalt: Salt, sndPass: HashedPassword, sndSalt: Salt) =>
      will(cleanUsers) {
        PostgresUsersStore.make[IO](transactor()).use { store =>
          val (fstUsername, sndUsername) = users
          for
            fstUuid <- store.store(fstUsername, fstPass, fstSalt).ccommit
            sndUuid <- store.store(sndUsername, sndPass, sndSalt).ccommit

            _ <- store.delete(fstUuid).ccommit

            fstUser <- store.fetchUser(fstUuid).ccommit
            sndUser <- store.fetchUser(sndUuid).ccommit
            _ <- IO {
              assertEquals(fstUser, none)
              assertEquals(sndUser, User(sndUuid, sndUsername).some)
            }
          yield ()
        }
      }
    }

  test("set user's `createdAt` and `updatedAt` field to the current timestamp during storage"):
    forAllF { (username: Username, password: HashedPassword, salt: Salt) =>
      will(cleanUsers) {
        PostgresUsersStore.make[IO](transactor()).use { store =>
          for
            uuid <- store.store(username, password, salt).ccommit

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
    forAllF {
      (username: Username,
       password: HashedPassword,
       salt: Salt,
       newUsername: Username,
       newPassword: HashedPassword,
       condition: Int
      ) =>
        will(cleanUsers) {
          PostgresUsersStore.make[IO](transactor()).use { store =>
            for
              uuid <- store.store(username, password, salt).ccommit

              _ <- condition match
                case 1 => store.updateUsername(uuid, newUsername).ccommit
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
    def fetchPassword(uuid: UUID): ConnectionIO[HashedPassword] =
      sql"SELECT password_hash FROM auth.users WHERE uuid = ${uuid}".query[HashedPassword].unique
    def fetchCreatedAt(uuid: UUID): ConnectionIO[Instant] =
      sql"SELECT created_at FROM auth.users WHERE uuid = ${uuid}".query[Instant].unique
    def fetchUpdatedAt(uuid: UUID): ConnectionIO[Instant] =
      sql"SELECT updated_at FROM auth.users WHERE uuid = ${uuid}".query[Instant].unique

  // a bcrypt hash approximation for efficience (store assumes correctness)
  given Arbitrary[HashedPassword] = Arbitrary(hashedPasswords)
  def hashedPasswords: Gen[HashedPassword] =
    Gen.listOfN(60, bcryptBase64Char).map(_.mkString).map(HashedPassword.unsafeFromString)

  private def bcryptBase64Char: Gen[Char] = Gen.oneOf(
    Gen.choose('A', 'Z'),
    Gen.choose('a', 'z'),
    Gen.choose('0', '9'),
    Gen.const('.'),
    Gen.const('/')
  )
