package org.fiume.sketch.storage.auth0.postgres

import cats.effect.IO
import cats.implicits.*
import doobie.ConnectionIO
import doobie.implicits.*
import doobie.postgres.implicits.*
import munit.ScalaCheckEffectSuite
import org.fiume.sketch.shared.auth0.{AccountState, Passwords, User, UserId}
import org.fiume.sketch.shared.auth0.Passwords.HashedPassword
import org.fiume.sketch.shared.auth0.User.*
import org.fiume.sketch.shared.auth0.testkit.PasswordsGens.given
import org.fiume.sketch.shared.auth0.testkit.UserGens.given
import org.fiume.sketch.storage.auth0.postgres.DoobieMappings.given
import org.fiume.sketch.storage.testkit.DockerPostgresSuite
import org.scalacheck.ShrinkLowPriority
import org.scalacheck.effect.PropF.forAllF

import java.time.Instant

class PostgresUsersStoreSpec
    extends ScalaCheckEffectSuite
    with DockerPostgresSuite
    with PostgresUsersStoreSpecContext
    with ShrinkLowPriority:

  override def scalaCheckTestParameters = super.scalaCheckTestParameters.withMinSuccessfulTests(10)

  test("stores credentials"):
    forAllF { (credentials: UserCredentials) =>
      will(cleanUsers) {
        PostgresUsersStore.make[IO](transactor()).use { store =>
          for
            uuid <- store.store(credentials).ccommit

            result <- store.fetchCredentials(credentials.username).ccommit
//
          yield assertEquals(result, UserCredentials.make(uuid, credentials).some)
        }
      }
    }

  test("fetches user account"):
    forAllF { (credentials: UserCredentials) =>
      will(cleanUsers) {
        PostgresUsersStore.make[IO](transactor()).use { store =>
          for
            uuid <- store.store(credentials).ccommit

            // TODO Extract an OptionSyntax
            result <- store.fetchAccount(credentials.username).map(_.get).ccommit
//
          yield
            assertEquals(result.uuid, uuid)
            assertEquals(result.credentials, credentials)
            result.state match
              case AccountState.Active(_) => assert(true)
              case _                      => fail(s"Expected AccountState.Active, got ${result.state}")
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
          yield assertEquals(result, newPassword)
        }
      }
    }

  test("marks account for deletion"):
    forAllF { (fstCreds: UserCredentials, sndCreds: UserCredentials) =>
      will(cleanUsers) {
        PostgresUsersStore.make[IO](transactor()).use { store =>
          for
            fstUuid <- store.store(fstCreds).ccommit
            sndUuid <- store.store(sndCreds).ccommit

            _ <- store.markForDeletion(fstUuid).ccommit

            // TODO Extract an OptionSyntax
            fstAccount <- store.fetchAccount(fstCreds.username).map(_.get).ccommit
            sndAccount <- store.fetchAccount(sndCreds.username).map(_.get).ccommit
          yield
            assert(sndAccount.isActive)
            assert(!fstAccount.isActive)
            fstAccount.state match
              case AccountState.SoftDeleted(_) => assert(true)
              case _                           => fail(s"Expected AccountState.SoftDeleted, got ${fstAccount.state}")
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
//
          yield assertEquals(createdAt, updatedAt)
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
          yield assert(
            updatedAt.isAfter(createdAt),
            clue = s"updatedAt=${updatedAt} should be after createdAt=${createdAt}"
          )
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
