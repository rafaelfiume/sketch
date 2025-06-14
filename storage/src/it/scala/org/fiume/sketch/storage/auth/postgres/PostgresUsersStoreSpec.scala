package org.fiume.sketch.storage.auth.postgres

import cats.effect.IO
import cats.implicits.*
import doobie.ConnectionIO
import doobie.implicits.*
import doobie.postgres.implicits.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.fiume.sketch.shared.auth.Passwords.HashedPassword
import org.fiume.sketch.shared.auth.User.*
import org.fiume.sketch.shared.auth.UserId
import org.fiume.sketch.shared.auth.accounts.{Account, AccountDeletionEvent, AccountState}
import org.fiume.sketch.shared.auth.testkit.AccountGens.given
import org.fiume.sketch.shared.auth.testkit.PasswordsGens.given
import org.fiume.sketch.shared.auth.testkit.UserGens
import org.fiume.sketch.shared.auth.testkit.UserGens.given
import org.fiume.sketch.shared.testkit.syntax.OptionSyntax.*
import org.fiume.sketch.storage.auth.postgres.DatabaseCodecs.given
import org.fiume.sketch.storage.testkit.DockerPostgresSuite
import org.scalacheck.ShrinkLowPriority
import org.scalacheck.effect.PropF.forAllF

import java.time.Instant

class PostgresUsersStoreSpec
    extends CatsEffectSuite
    with ScalaCheckEffectSuite
    with PostgresUsersStoreSpecContext
    with ShrinkLowPriority:

  override def scalaCheckTestParameters = super.scalaCheckTestParameters.withMinSuccessfulTests(1)

  test("creates account and fetches it by username"):
    forAllF { (credentials: UserCredentials) =>
      will(cleanStorage) {
        PostgresUsersStore.make[IO](transactor()).use { store =>
          for
            uuid <- store.createAccount(credentials).ccommit

            result <- store.fetchAccount(credentials.username).map(_.someOrFail).ccommit
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
      will(cleanStorage) {
        PostgresUsersStore.make[IO](transactor()).use { store =>
          for
            uuid <- store.createAccount(credentials).ccommit

            _ <- store.updatePassword(uuid, newPassword).ccommit

            result <- fetchPassword(uuid).ccommit
          yield assertEquals(result, newPassword)
        }
      }
    }

  test("updates user account"):
    forAllF { (credentials: UserCredentials, account: Account) =>
      will(cleanStorage) {
        PostgresUsersStore.make[IO](transactor()).use { store =>
          for
            uuid <- store.createAccount(credentials).ccommit
            updated = account.copy(uuid = uuid)

            _ <- store.updateAccount(updated).ccommit

            result <- store.fetchAccount(uuid).map(_.someOrFail).ccommit
          yield assertEquals(result, updated)
        }
      }
    }

  test("deletes user account"):
    forAllF { (credentials: UserCredentials) =>
      will(cleanStorage) {
        PostgresUsersStore.make[IO](transactor()).use { store =>
          for
            userId <- store.createAccount(credentials).ccommit

            result <- store.deleteAccount(userId).ccommit

            account <- store.fetchAccount(userId).ccommit
          yield
            assertEquals(result, userId.some)
            assert(account.isEmpty)
        }
      }
    }

trait PostgresUsersStoreSpecContext extends DockerPostgresSuite:
  def cleanStorage: ConnectionIO[Unit] =
    sql"TRUNCATE TABLE auth.users".update.run.void

  def fetchPassword(uuid: UserId): ConnectionIO[HashedPassword] =
      sql"SELECT password_hash FROM auth.users WHERE uuid = ${uuid}".query[HashedPassword].unique

  def fetchCreatedAt(uuid: UserId): ConnectionIO[Instant] =
      sql"SELECT created_at FROM auth.users WHERE uuid = ${uuid}".query[Instant].unique

  def fetchUpdatedAt(uuid: UserId): ConnectionIO[Instant] =
      sql"SELECT updated_at FROM auth.users WHERE uuid = ${uuid}".query[Instant].unique

  def fetchScheduledAccountDeletion(uuid: UserId): ConnectionIO[Option[AccountDeletionEvent.Scheduled]] =
      sql"SELECT * FROM auth.account_deletion_scheduled_events WHERE user_id = ${uuid}"
        .query[AccountDeletionEvent.Scheduled]
        .option
