package org.fiume.sketch.storage.auth0.postgres

import cats.effect.IO
import cats.implicits.*
import doobie.ConnectionIO
import doobie.implicits.*
import doobie.postgres.implicits.*
import munit.ScalaCheckEffectSuite
import org.fiume.sketch.shared.auth0.{Passwords, User}
import org.fiume.sketch.shared.auth0.Passwords.{HashedPassword, Salt}
import org.fiume.sketch.shared.auth0.User.*
import org.fiume.sketch.shared.auth0.algebras.UsersStore
import org.fiume.sketch.shared.auth0.test.PasswordsGens.HashedPasswords.given
import org.fiume.sketch.shared.auth0.test.PasswordsGens.Salts.given
import org.fiume.sketch.shared.auth0.test.UserGens.*
import org.fiume.sketch.shared.auth0.test.UserGens.Usernames.*
import org.fiume.sketch.shared.auth0.test.UserGens.Usernames.given
import org.fiume.sketch.shared.auth0.test.UserGens.given
import org.fiume.sketch.storage.auth0.postgres.DoobieMappings.given
import org.fiume.sketch.storage.auth0.postgres.PostgresUsersStore.*
import org.fiume.sketch.storage.test.support.DockerPostgresSuite
import org.scalacheck.{Arbitrary, Gen, ShrinkLowPriority}
import org.scalacheck.effect.PropF.forAllF

import java.time.Instant
import java.util.UUID

class PostgresUsersStoreSpec
    extends ScalaCheckEffectSuite
    with DockerPostgresSuite
    with PostgresUsersStoreSpecContext
    with ShrinkLowPriority:

  test("store and fetch user"):
    forAllF { (credentials: UserCredentials) =>
      will(cleanUsers) {
        PostgresUsersStore.make[IO](transactor()).use { store =>
          for
            uuid <- store.store(credentials).ccommit

            result <- store.fetchUser(uuid).ccommit

            _ <- IO {
              assertEquals(result, User(uuid, credentials.username).some)
            }
          yield ()
        }
      }
    }

  test("store and fetch credentials"):
    forAllF { (credentials: UserCredentials) =>
      will(cleanUsers) {
        PostgresUsersStore.make[IO](transactor()).use { store =>
          for
            uuid <- store.store(credentials).ccommit

            result <- store.fetchCredentials(credentials.username).ccommit

            _ <- IO {
              assertEquals(result,
                           UserCredentials.withUuid(uuid, credentials.username, credentials.hashedPassword, credentials.salt).some
              )
            }
          yield ()
        }
      }
    }

  test("update user password"):
    forAllF { (credentials: UserCredentials, newPassword: HashedPassword) =>
      will(cleanUsers) {
        PostgresUsersStore.make[IO](transactor()).use { store =>
          for
            uuid <- store.store(credentials).ccommit

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
        fst <- validUsernames
        snd <- validUsernames
      yield (fst, snd)).suchThat { case (fst, snd) => fst != snd }
    )
    forAllF { (fstCreds: UserCredentials, sndCreds: UserCredentials) =>
      will(cleanUsers) {
        PostgresUsersStore.make[IO](transactor()).use { store =>
          for
            fstUuid <- store.store(fstCreds).ccommit
            sndUuid <- store.store(sndCreds).ccommit

            _ <- store.delete(fstUuid).ccommit

            fstStoredCreds <- store.fetchCredentials(fstCreds.username).ccommit
            sndStoredCreds <- store.fetchCredentials(sndCreds.username).ccommit
            _ <- IO {
              assertEquals(fstStoredCreds, none)
              assertEquals(
                sndStoredCreds,
                UserCredentials.withUuid(sndUuid, sndCreds.username, sndCreds.hashedPassword, sndCreds.salt).some
              )
            }
          yield ()
        }
      }
    }

  test("set user's `createdAt` and `updatedAt` field to the current timestamp during storage"):
    forAllF { (credentials: UserCredentials) =>
      will(cleanUsers) {
        PostgresUsersStore.make[IO](transactor()).use { store =>
          for
            uuid <- store.store(credentials).ccommit

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
    forAllF { (credentials: UserCredentials, newPassword: HashedPassword) =>
      will(cleanUsers) {
        PostgresUsersStore.make[IO](transactor()).use { store =>
          for
            uuid <- store.store(credentials).ccommit

            _ <- store.updatePassword(uuid, newPassword).ccommit

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
