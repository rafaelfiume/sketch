package org.fiume.sketch.storage.auth.postgres

import cats.effect.IO
import cats.implicits.*
import doobie.ConnectionIO
import doobie.implicits.*
import munit.ScalaCheckEffectSuite
import org.fiume.sketch.shared.auth.User.*
import org.fiume.sketch.shared.auth.testkit.UserGens
import org.fiume.sketch.shared.auth.testkit.UserGens.given
import org.fiume.sketch.shared.testkit.ClockContext
import org.fiume.sketch.shared.testkit.syntax.EitherSyntax.*
import org.fiume.sketch.shared.testkit.syntax.OptionSyntax.*
import org.fiume.sketch.storage.testkit.DockerPostgresSuite
import org.scalacheck.ShrinkLowPriority
import org.scalacheck.effect.PropF.forAllF

import scala.concurrent.duration.*

class PostgresEventConsumerSpec
    extends ScalaCheckEffectSuite
    with ClockContext
    with PostgresEventStoreSpecContext
    with ShrinkLowPriority:

  override def scalaCheckTestParameters = super.scalaCheckTestParameters.withMinSuccessfulTests(1)

  test("claims next job and return it to the queue if processing fails"):
    forAllF { () =>
      will(cleanStorage) {
        (
          PostgresUsersStore.make[IO](transactor(), makeFrozenClock()),
          PostgresEventConsumer.make[IO]()
        ).tupled.use { case (usersStore, eventsConsumer) =>
          // given
          val numQueuedJobs = 1000
          val now = 0.seconds
          for
            queuedJobs <- fs2.Stream
              .range(0, numQueuedJobs)
              .covary[IO]
              .parEvalMapUnbounded { _ =>
                val user = UserGens.credentials.sample.someOrFail
                usersStore.createAccountMarkedForDeletion(user, now).map(_.rightOrFail.uuid)
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
                  eventsConsumer
                    .claimNextJob()
                    .flatMap { job =>
                      if i % 2 == 0 then usersStore.lift { RuntimeException(s"failed: ${job.map(_.uuid)}").raiseError }
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
        (
          PostgresUsersStore.make[IO](transactor(), makeFrozenClock()),
          PostgresEventConsumer.make[IO]()
        ).tupled.use { case (usersStore, eventsConsumer) =>
          val now = 0.seconds
          val future = 60.seconds
          for
            _ <- usersStore.createAccountMarkedForDeletion(fstUser, future)
            _ <- usersStore.createAccountMarkedForDeletion(sndUser, future)
            trdScheduledDeletion <- usersStore.createAccountMarkedForDeletion(trdUser, now)
            _ <- usersStore.createAccountMarkedForDeletion(fthUser, future)

            result <- fs2.Stream.repeatEval { eventsConsumer.claimNextJob().ccommit }.unNone.take(1).compile.toList
//
          yield assertEquals(result.toSet, List(trdScheduledDeletion.rightOrFail).toSet)
        }
      }
    }

trait PostgresEventStoreSpecContext extends DockerPostgresSuite:
  def cleanStorage: ConnectionIO[Unit] =
    sql"TRUNCATE TABLE auth.account_permanent_deletion_queue, auth.users".update.run.void

  extension (store: PostgresUsersStore[IO])
    def createAccountMarkedForDeletion(user: UserCredentials, timeUntilPermanentDeletion: Duration) =
      store
        .createAccount(user)
        .flatMap { store.markForDeletion(_, timeUntilPermanentDeletion) }
        .ccommit
