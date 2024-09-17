package org.fiume.sketch.storage.auth0.postgres

import cats.effect.IO
import cats.implicits.*
import doobie.ConnectionIO
import doobie.implicits.*
import doobie.postgres.implicits.*
import munit.ScalaCheckEffectSuite
import org.fiume.sketch.shared.auth0.{AccountState, Passwords, User, UserId}
import org.fiume.sketch.shared.auth0.AccountState.SoftDeleted
import org.fiume.sketch.shared.auth0.Passwords.HashedPassword
import org.fiume.sketch.shared.auth0.User.*
import org.fiume.sketch.shared.auth0.testkit.PasswordsGens.given
import org.fiume.sketch.shared.auth0.testkit.UserGens.given
import org.fiume.sketch.shared.testkit.ClockContext
import org.fiume.sketch.shared.testkit.syntax.OptionSyntax.*
import org.fiume.sketch.storage.auth0.postgres.DoobieMappings.given
import org.fiume.sketch.storage.testkit.DockerPostgresSuite
import org.scalacheck.ShrinkLowPriority
import org.scalacheck.effect.PropF.forAllF

import java.time.Instant
import java.time.temporal.ChronoUnit.MILLIS

class PostgresUsersStoreSpec
    extends ScalaCheckEffectSuite
    with DockerPostgresSuite
    with ClockContext
    with PostgresUsersStoreSpecContext
    with ShrinkLowPriority:

  override def scalaCheckTestParameters = super.scalaCheckTestParameters.withMinSuccessfulTests(10)

  test("stores credentials"):
    forAllF { (credentials: UserCredentials) =>
      will(cleanUsers) {
        PostgresUsersStore.make[IO](transactor(), makeAnytime()).use { store =>
          for
            uuid <- store.store(credentials).ccommit

            result <- store.fetchCredentials(credentials.username).ccommit
//
          yield assertEquals(result.someOrFail, UserCredentials.make(uuid, credentials))
        }
      }
    }

  test("fetches user account"):
    forAllF { (credentials: UserCredentials) =>
      will(cleanUsers) {
        PostgresUsersStore.make[IO](transactor(), makeAnytime()).use { store =>
          for
            uuid <- store.store(credentials).ccommit

            result <- store.fetchAccount(credentials.username).ccommit.map(_.someOrFail)
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
        PostgresUsersStore.make[IO](transactor(), makeAnytime()).use { store =>
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
        val deletedAt = Instant.now()
        val frozenTime = makeFrozenTime(deletedAt)
        PostgresUsersStore.make[IO](transactor(), frozenTime).use { store =>
          for
            fstUuid <- store.store(fstCreds).ccommit
            sndUuid <- store.store(sndCreds).ccommit

            _ <- store.markForDeletion(fstUuid).ccommit

            // TODO Extract an OptionSyntax
            fstAccount <- store.fetchAccount(fstCreds.username).ccommit.map(_.someOrFail)
            sndAccount <- store.fetchAccount(sndCreds.username).ccommit.map(_.someOrFail)
          yield
            assert(sndAccount.isActive)
            assert(!fstAccount.isActive)
            assertEquals(fstAccount.state, SoftDeleted(deletedAt.truncatedTo(MILLIS)))
        }
      }
    }

  test("timestamps createdAt and updatedAt upon storage"):
    forAllF { (credentials: UserCredentials) =>
      will(cleanUsers) {
        PostgresUsersStore.make[IO](transactor(), makeAnytime()).use { store =>
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
        PostgresUsersStore.make[IO](transactor(), makeAnytime()).use { store =>
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
