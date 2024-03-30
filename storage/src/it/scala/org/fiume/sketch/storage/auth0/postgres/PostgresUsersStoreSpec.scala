package org.fiume.sketch.storage.auth0.postgres

import cats.effect.IO
import cats.implicits.*
import doobie.ConnectionIO
import doobie.implicits.*
import doobie.postgres.implicits.*
import munit.ScalaCheckEffectSuite
import org.fiume.sketch.shared.auth0.{Passwords, User, UserId}
import org.fiume.sketch.shared.auth0.Passwords.HashedPassword
import org.fiume.sketch.shared.auth0.User.*
import org.fiume.sketch.shared.auth0.testkit.PasswordsGens.given
import org.fiume.sketch.shared.auth0.testkit.UserGens.given
import org.fiume.sketch.storage.auth0.postgres.DoobieMappings.given
import org.fiume.sketch.storage.auth0.postgres.PostgresUsersStore.*
import org.fiume.sketch.storage.testkit.DockerPostgresSuite
import org.scalacheck.ShrinkLowPriority
import org.scalacheck.effect.PropF.forAllF

import java.time.Instant

class PostgresUsersStoreSpec
    extends ScalaCheckEffectSuite
    with DockerPostgresSuite
    with PostgresUsersStoreSpecContext
    with ShrinkLowPriority:

  test("fetches user from stored credentials"):
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

  test("fetches stored credentials"):
    forAllF { (credentials: UserCredentials) =>
      will(cleanUsers) {
        PostgresUsersStore.make[IO](transactor()).use { store =>
          for
            uuid <- store.store(credentials).ccommit

            result <- store.fetchCredentials(credentials.username).ccommit

            _ <- IO {
              assertEquals(result, UserCredentials.withUuid(uuid, credentials).some)
            }
          yield ()
        }
      }
    }

  test("updates user password"):
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

  test("deletes credentials"):
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
              assertEquals(sndStoredCreds, UserCredentials.withUuid(sndUuid, sndCreds).some)
            }
          yield ()
        }
      }
    }

  test("timestamps createdAt and updatedAt upon storage"):
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

  test("timestamps updatedAt upon update"):
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
    def fetchPassword(uuid: UserId): ConnectionIO[HashedPassword] =
      sql"SELECT password_hash FROM auth.users WHERE uuid = ${uuid}".query[HashedPassword].unique
    def fetchCreatedAt(uuid: UserId): ConnectionIO[Instant] =
      sql"SELECT created_at FROM auth.users WHERE uuid = ${uuid}".query[Instant].unique
    def fetchUpdatedAt(uuid: UserId): ConnectionIO[Instant] =
      sql"SELECT updated_at FROM auth.users WHERE uuid = ${uuid}".query[Instant].unique
