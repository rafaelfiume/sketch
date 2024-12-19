package org.fiume.sketch.storage.auth.postgres

import cats.effect.IO
import cats.implicits.*
import doobie.ConnectionIO
import doobie.implicits.*
import munit.ScalaCheckEffectSuite
import org.fiume.sketch.shared.auth.UserId
import org.fiume.sketch.shared.auth.accounts.jobs.AccountDeletionEvent
import org.fiume.sketch.shared.auth.testkit.UserGens
import org.fiume.sketch.shared.auth.testkit.UserGens.given
import org.fiume.sketch.shared.testkit.ClockContext
import org.fiume.sketch.shared.testkit.syntax.OptionSyntax.*
import org.fiume.sketch.storage.testkit.DockerPostgresSuite
import org.scalacheck.ShrinkLowPriority
import org.scalacheck.effect.PropF.forAllF

import java.time.Instant

class PostgresEventsStoreSpec
    extends ScalaCheckEffectSuite
    with ClockContext
    with PostgresEventStoreSpecContext
    with ShrinkLowPriority:

  override def scalaCheckTestParameters = super.scalaCheckTestParameters.withMinSuccessfulTests(1)

  test("claims next job and return it to the queue if processing fails"):
    forAllF { () =>
      will(cleanStorage) {
        PostgresEventsStore.make[IO]().use { eventsStore =>
          // given
          val numQueuedJobs = 1000
          val now = Instant.now()
          for
            queuedJobs <- fs2.Stream
              .range(0, numQueuedJobs)
              .covary[IO]
              .parEvalMapUnbounded { _ =>
                val userId = UserGens.userIds.sample.someOrFail
                eventsStore.produceEvent(AccountDeletionEvent.Unscheduled(userId, now)).map(_.uuid).ccommit
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
                  eventsStore
                    .claimNextJob()
                    .flatMap { job =>
                      if i % 2 == 0 then lift { RuntimeException(s"failed: ${job.map(_.uuid)}").raiseError }
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
    forAllF { (fstUserId: UserId, sndUserId: UserId, trdUserId: UserId, fthUserId: UserId) =>
      will(cleanStorage) {
        PostgresEventsStore.make[IO]().use { eventsStore =>
          val now = Instant.now()
          val future = now.plusSeconds(60)
          for
            _ <- eventsStore.produceEvent(AccountDeletionEvent.Unscheduled(fstUserId, future)).ccommit
            _ <- eventsStore.produceEvent(AccountDeletionEvent.Unscheduled(sndUserId, future)).ccommit
            _ <- eventsStore.produceEvent(AccountDeletionEvent.Unscheduled(trdUserId, now)).ccommit
            _ <- eventsStore.produceEvent(AccountDeletionEvent.Unscheduled(fthUserId, future)).ccommit

            result <- fs2.Stream
              .repeatEval {
                eventsStore.claimNextJob().ccommit
              }
              .unNone
              .take(1)
              .compile
              .toList
//
          yield assertEquals(result.map(_.userId).toSet, List(trdUserId).toSet)
        }
      }
    }

trait PostgresEventStoreSpecContext extends DockerPostgresSuite:
  def cleanStorage: ConnectionIO[Unit] =
    sql"TRUNCATE TABLE auth.account_permanent_deletion_queue".update.run.void
