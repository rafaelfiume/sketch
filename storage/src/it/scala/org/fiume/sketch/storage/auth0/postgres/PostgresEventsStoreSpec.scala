package org.fiume.sketch.storage.auth.postgres

import cats.effect.IO
import cats.implicits.*
import doobie.ConnectionIO
import doobie.implicits.*
import munit.ScalaCheckEffectSuite
import org.fiume.sketch.shared.auth.UserId
import org.fiume.sketch.shared.auth.accounts.AccountDeletionEvent
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

  // TODO We are testing two different properties here. It would be better to split them into two different tests.
  test("consumes next event and return it to the queue if processing fails"): // TODO Is queue the correct terminology here?
    forAllF { () =>
      will(cleanStorage) {
        PostgresEventsStore.make[IO]().use { eventsStore =>
          // given
          val numQueuedEvents = 1000
          val now = Instant.now()
          for
            queuedEvents <- fs2.Stream
              .range(0, numQueuedEvents)
              .covary[IO]
              .parEvalMapUnbounded { _ =>
                val userId = UserGens.userIds.sample.someOrFail
                eventsStore.produceEvent(AccountDeletionEvent.Unscheduled(userId, now)).map(_.uuid).ccommit
              }
              // .evalTap { eventId => IO.println(s"new event: $eventId") } // uncomment to debug
              .compile
              .toList

            // when
            result <-
              fs2.Stream
                // process twice the number of queued events + a few to compensate for the failures
                .range(0, 2 * numQueuedEvents + 50)
                .covary[IO]
                .parEvalMapUnorderedUnbounded { i =>
                  eventsStore
                    .consumeEvent()
                    .flatMap { event =>
                      if i % 2 == 0 then lift { RuntimeException(s"failed: ${event.map(_.uuid)}").raiseError }
                      else event.pure[ConnectionIO]
                    }
                    .ccommit
                    .handleErrorWith { error =>
                      // IO.println(error.getMessage()) >> // uncomment to debug
                      none.pure[IO]
                    }
                }
                .map(_.map(_.uuid))
                // .evalTap { eventId => IO.println(s"consumed event: ${eventId}") } // uncomment to debug
                .unNone
                .compile
                .toList

          // then
          yield
            assert(result.size == numQueuedEvents, clue = s"Expected $numQueuedEvents events, got ${result.size}")
            assertEquals(result.toSet, queuedEvents.toSet)
        }
      }
    }

  test("skips event if permanent deletion is not yet due"):
    forAllF { (fstUserId: UserId, sndUserId: UserId, trdUserId: UserId, fthUserId: UserId) =>
      will(cleanStorage) {
        PostgresEventsStore.make[IO]().use { eventsStore =>
          val due = Instant.now()
          val notYetDue = due.plusSeconds(60)
          for
            _ <- eventsStore.produceEvent(AccountDeletionEvent.Unscheduled(fstUserId, notYetDue)).ccommit
            _ <- eventsStore.produceEvent(AccountDeletionEvent.Unscheduled(sndUserId, notYetDue)).ccommit
            _ <- eventsStore.produceEvent(AccountDeletionEvent.Unscheduled(trdUserId, due)).ccommit
            _ <- eventsStore.produceEvent(AccountDeletionEvent.Unscheduled(fthUserId, notYetDue)).ccommit

            result <- fs2.Stream
              .repeatEval {
                eventsStore.consumeEvent().ccommit
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
