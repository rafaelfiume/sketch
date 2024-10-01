package org.fiume.sketch.storage.auth0.postgres

import cats.effect.IO
import cats.implicits.*
import doobie.ConnectionIO
import doobie.implicits.*
import doobie.postgres.implicits.*
import munit.ScalaCheckEffectSuite
import org.fiume.sketch.shared.auth0.domain.{AccountState, Passwords, User, UserId}
import org.fiume.sketch.shared.auth0.domain.AccountState.SoftDeleted
import org.fiume.sketch.shared.auth0.domain.Passwords.HashedPassword
import org.fiume.sketch.shared.auth0.domain.User.*
import org.fiume.sketch.shared.auth0.jobs.ScheduledAccountDeletion
import org.fiume.sketch.shared.auth0.testkit.PasswordsGens.given
import org.fiume.sketch.shared.auth0.testkit.UserGens
import org.fiume.sketch.shared.auth0.testkit.UserGens.given
import org.fiume.sketch.shared.jobs.JobId
import org.fiume.sketch.shared.testkit.ClockContext
import org.fiume.sketch.shared.testkit.syntax.OptionSyntax.*
import org.fiume.sketch.storage.auth0.postgres.DatabaseCodecs.given
import org.fiume.sketch.storage.testkit.DockerPostgresSuite
import org.scalacheck.ShrinkLowPriority
import org.scalacheck.effect.PropF.forAllF

import java.time.Instant
import java.time.temporal.ChronoUnit.MILLIS
import scala.concurrent.duration.*

class PostgresUsersStoreSpec
    extends ScalaCheckEffectSuite
    with ClockContext
    with PostgresUsersStoreSpecContext
    with ShrinkLowPriority:

  override def scalaCheckTestParameters = super.scalaCheckTestParameters.withMinSuccessfulTests(1)

  test("creates account and fetches it by username"):
    forAllF { (credentials: UserCredentials) =>
      will(cleanStorage) {
        PostgresUsersStore.make[IO](transactor(), makeFrozenClock()).use { store =>
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
      will(cleanStorage) {
        PostgresUsersStore.make[IO](transactor(), makeFrozenClock()).use { store =>
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
      will(cleanStorage) {
        val deletedAt = Instant.now()
        val frozenClock = makeFrozenClock(deletedAt)
        val permantDeletionDelay = 1.second
        PostgresUsersStore.make[IO](transactor(), frozenClock).use { store =>
          for
            fstUserId <- store.store(fstCreds).ccommit
            sndUserId <- store.store(sndCreds).ccommit

            _ <- store.markForDeletion(fstUserId, permantDeletionDelay).ccommit

            fstAccount <- store.fetchAccount(fstUserId).ccommit.map(_.someOrFail)
            fstScheduledAccountDeletion <- store.fetchScheduledAccountDeletion(fstUserId).ccommit.map(_.someOrFail)
            sndAccount <- store.fetchAccount(sndUserId).ccommit.map(_.someOrFail)
            sndScheduledAccountDeletion <- store.fetchScheduledAccountDeletion(sndUserId).ccommit
          yield
            assert(fstAccount.isMarkedForDeletion)
            assertEquals(fstAccount.state, SoftDeleted(deletedAt.truncatedTo(MILLIS)))
            val permanentDeletionAt = deletedAt.plusSeconds(permantDeletionDelay.toSeconds).truncatedTo(MILLIS)
            assertEquals(
              fstScheduledAccountDeletion,
              ScheduledAccountDeletion(
                fstScheduledAccountDeletion.uuid, // ignore checking the job id generated by Postgres
                fstUserId,
                permanentDeletionAt
              )
            )
            assert(sndAccount.isActive)
            assert(sndScheduledAccountDeletion.isEmpty, clue = "Expected no scheduled deletion for the second account")
        }
      }
    }

  test("restores account"):
    forAllF { (credentials: UserCredentials) =>
      will(cleanStorage) {
        PostgresUsersStore.make[IO](transactor(), makeFrozenClock()).use { store =>
          for
            userId <- store.store(credentials).ccommit
            _ <- store.markForDeletion(userId, 1.day).ccommit

            _ <- store.restoreAccount(userId).ccommit

            fstAccount <- store.fetchAccount(userId).ccommit.map(_.someOrFail)
            scheduledAccountDeletion <- store.fetchScheduledAccountDeletion(userId).ccommit
          yield
            assert(fstAccount.isActive)
            assert(scheduledAccountDeletion.isEmpty, clue = "Expected no scheduled deletion for the restored account")
        }
      }
    }

  test("deletes user account"):
    forAllF { (credentials: UserCredentials) =>
      will(cleanStorage) {
        PostgresUsersStore.make[IO](transactor(), makeFrozenClock()).use { store =>
          for
            userId <- store.store(credentials).ccommit

            _ <- store.delete(userId).ccommit

            account <- store.fetchAccount(userId).ccommit
          yield assert(account.isEmpty)
        }
      }
    }

  test("claims next job and return it to the queue if processing fails"):
    forAllF { () =>
      will(cleanStorage) {
        PostgresUsersStore.make[IO](transactor(), makeFrozenClock()).use { store =>
          // given
          val numQueuedJobs = 1000
          for
            queuedJobs <- fs2.Stream
              .range(0, numQueuedJobs)
              .covary[IO]
              .parEvalMapUnbounded { _ =>
                val user = UserGens.credentials.sample.someOrFail
                store.markAccountForDeletion(user, permanentDeletionAt = Instant.now()).map(_.uuid)
              }
              // .evalTap { jobId => IO.println(s"new job: $jobId") } // uncomment to debug
              .compile
              .toList

            // when
            result <-
              fs2.Stream
                // runs twice the number of queued jobs + a few to compensate for the failures
                .range(0, 2 * numQueuedJobs + 50)
                .covary[IO]
                .parEvalMapUnorderedUnbounded { i =>
                  store
                    .claimNextJob()
                    .flatMap { job =>
                      if i % 2 == 0 then store.lift { RuntimeException(s"failed: ${job.map(_.uuid)}").raiseError }
                      else job.pure[ConnectionIO]
                    }
                    .ccommit
                    .handleErrorWith { error =>
                      // IO.println(error.getMessage()) >> // uncomment to debug
                      none.pure[IO]
                    }
                }
                .map(_.map(_.uuid))
                // .evalTap { jobId => IO.println(s"claimed job: ${jobId}") } // uncomment to debug
                .unNone
                .compile
                .toList

          // then
          yield
            assert(result.size == numQueuedJobs, clue = s"Expected $numQueuedJobs jobs, got ${result.size}")
            assertEquals(result.toSet, queuedJobs.toSet)
        }
      }
    }

  test("skips job if permanent deletion is not yet due"):
    forAllF { (fstUser: UserCredentials, sndUser: UserCredentials, trdUser: UserCredentials, fthUser: UserCredentials) =>
      will(cleanStorage) {
        PostgresUsersStore.make[IO](transactor(), makeFrozenClock()).use { store =>
          val futureDate = Instant.now().plusSeconds(60)
          for
            _ <- store.markAccountForDeletion(fstUser, permanentDeletionAt = futureDate)
            _ <- store.markAccountForDeletion(sndUser, permanentDeletionAt = futureDate)
            trdScheduledDeletion <- store.markAccountForDeletion(trdUser, permanentDeletionAt = Instant.now())
            _ <- store.markAccountForDeletion(fthUser, permanentDeletionAt = futureDate)

            result <- fs2.Stream.repeatEval { store.claimNextJob().ccommit }.unNone.take(1).compile.toList
//
          yield assertEquals(result.toSet, List(trdScheduledDeletion).toSet)
        }
      }
    }

  test("timestamps createdAt and updatedAt upon storage"):
    forAllF { (credentials: UserCredentials) =>
      will(cleanStorage) {
        PostgresUsersStore.make[IO](transactor(), makeFrozenClock()).use { store =>
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
      will(cleanStorage) {
        PostgresUsersStore.make[IO](transactor(), makeFrozenClock()).use { store =>
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

trait PostgresUsersStoreSpecContext extends DockerPostgresSuite:
  def cleanStorage: ConnectionIO[Unit] =
    sql"TRUNCATE TABLE auth.account_permanent_deletion_queue, auth.users".update.run.void

  extension (store: PostgresUsersStore[IO])
    def fetchPassword(uuid: UserId): ConnectionIO[HashedPassword] =
      sql"SELECT password_hash FROM auth.users WHERE uuid = ${uuid}".query[HashedPassword].unique

    def fetchCreatedAt(uuid: UserId): ConnectionIO[Instant] =
      sql"SELECT created_at FROM auth.users WHERE uuid = ${uuid}".query[Instant].unique

    def fetchUpdatedAt(uuid: UserId): ConnectionIO[Instant] =
      sql"SELECT updated_at FROM auth.users WHERE uuid = ${uuid}".query[Instant].unique

    def fetchScheduledAccountDeletion(uuid: UserId): ConnectionIO[Option[ScheduledAccountDeletion]] =
      sql"SELECT * FROM auth.account_permanent_deletion_queue WHERE user_id = ${uuid}"
        .query[ScheduledAccountDeletion]
        .option

    def markAccountForDeletion(user: UserCredentials, permanentDeletionAt: Instant): IO[ScheduledAccountDeletion] =
      store
        .store(user)
        .flatMap { store.schedulePermanentDeletion(_, permanentDeletionAt) }
        .ccommit
