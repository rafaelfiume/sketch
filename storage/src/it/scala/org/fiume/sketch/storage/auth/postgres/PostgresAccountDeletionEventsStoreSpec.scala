package org.fiume.sketch.storage.auth.postgres

import cats.effect.IO
import cats.implicits.*
import doobie.ConnectionIO
import doobie.implicits.*
import munit.CatsEffectSuite
import org.fiume.sketch.shared.auth.UserId
import org.fiume.sketch.shared.auth.accounts.AccountDeletionEvent
import org.fiume.sketch.shared.auth.testkit.UserGens
import org.fiume.sketch.shared.auth.testkit.UserGens.given
import org.fiume.sketch.shared.common.events.EventId
import org.fiume.sketch.shared.testkit.ClockContext
import org.fiume.sketch.shared.testkit.syntax.OptionSyntax.*
import org.fiume.sketch.storage.auth.postgres.DatabaseCodecs.given
import org.fiume.sketch.storage.testkit.DockerPostgresSuite
import org.scalacheck.effect.PropF.forAllF

import java.time.Instant

class PostgresAccountDeletionEventsStoreSpec
    extends CatsEffectSuite
    with ClockContext
    with PostgresAccountDeletionEventsStoreSpecContext:

  // Notes

  // No processing order guarantees are provided by the current implementation.

  test("consumes next event with exactly-once semantics"):
    will(cleanStorage) {
      PostgresAccountDeletionEventsStore.make[IO]().use { eventStore =>
        // given
        val numEvents = 1000
        val due = Instant.now()
        for
          delayedMessages <- fs2.Stream
            .range(0, numEvents)
            .covary[IO]
            .parEvalMapUnbounded { _ =>
              val userId = UserGens.userIds.sample.someOrFail
              eventStore.produceEvent(AccountDeletionEvent.ToSchedule(userId, due)).ccommit
            }
            // .evalTap { eventId => IO.println(s"new event: $eventId") } // uncomment to debug
            .compile
            .toList

          // when
          result <-
            fs2.Stream
              .range(0, numEvents)
              .covary[IO]
              .parEvalMapUnorderedUnbounded { _ => eventStore.consumeEvent().ccommit }
              // .evalTap { eventId => IO.println(s"consumed event: ${eventId}") } // uncomment to debug
              .unNone
              .compile
              .toList

        // then
        yield
          assert(result.size == numEvents, clue = s"expected $numEvents events, got ${result.size}")
          assertEquals(result.toSet, delayedMessages.toSet)
      }
    }

  // The larger the number of events sent back for processing, the greater the chances of delaying or blocking other events.
  // Ideally, failed events should be retried using exponential backoff with a maximum number of retries.
  test("event becomes available for reprocessing if its processing fails"):
    will(cleanStorage) {
      PostgresAccountDeletionEventsStore.make[IO]().use { eventStore =>
        // given
        val numEvents = 1000
        val due = Instant.now()
        val errorFrequency = 2
        for
          /*
           * If `Ref` seems overkill and that a simple `mutable.Set.empty[EventId]` would be sufficient,
           * consider that the latter is not concurrent-safe and makes the test flaky.
           */
          successfullyProcessedEventIdsRef <- IO.ref(Set.empty[EventId])
          atLeastOnceFailedEventIdsRef <- IO.ref(Set.empty[EventId])
          sentEventIds <- fs2.Stream
            .range(0, numEvents)
            .covary[IO]
            .parEvalMapUnbounded { _ =>
              val userId = UserGens.userIds.sample.someOrFail
              eventStore.produceEvent(AccountDeletionEvent.ToSchedule(userId, due)).map(_.uuid).ccommit
            }
            .compile
            .toList

          // when
          result <-
            fs2.Stream
              .range(0, numEvents)
              .covary[IO]
              .parEvalMapUnorderedUnbounded { i =>
                eventStore
                  .consumeEvent()
                  .flatMap { event =>
                    if i % errorFrequency == 0 then
                      lift {
                        atLeastOnceFailedEventIdsRef.update(s => event.fold(s)(s + _.uuid)) *>
                          RuntimeException(s"failed: ${event.map(_.uuid)}").raiseError
                      }
                    else
                      lift { successfullyProcessedEventIdsRef.update(s => event.fold(s)(s + _.uuid)) } *>
                        event.pure[ConnectionIO]
                  }
                  .ccommit
                  .handleErrorWith { _ => none.pure[IO] }
              }
              .map(_.map(_.uuid))
              .unNone
              .compile
              .toList

          // then
          atLeastOnceFailedEventIds <- atLeastOnceFailedEventIdsRef.get
          successfullyProcessedEventIds <- successfullyProcessedEventIdsRef.get
          pendingEventIds <- fetchPendingEvents().ccommit.map(_.toSet.map(_.uuid))
          expectedPendingEventIds = sentEventIds.toSet -- successfullyProcessedEventIds
        yield
          assert(
            atLeastOnceFailedEventIds.find(successfullyProcessedEventIds.contains).isDefined,
            clue = "retries processing of failed events"
          )
          assertEquals(pendingEventIds, expectedPendingEventIds)
      }
    }

  test("skips event if permanent deletion is not yet due"):
    forAllF { (fstUserId: UserId, sndUserId: UserId, trdUserId: UserId, fthUserId: UserId) =>
      will(cleanStorage) {
        PostgresAccountDeletionEventsStore.make[IO]().use { eventStore =>
          val due = Instant.now()
          val notYetDue = due.plusSeconds(60)
          for
            _ <- eventStore.produceEvent(AccountDeletionEvent.ToSchedule(fstUserId, notYetDue)).ccommit
            _ <- eventStore.produceEvent(AccountDeletionEvent.ToSchedule(sndUserId, notYetDue)).ccommit
            _ <- eventStore.produceEvent(AccountDeletionEvent.ToSchedule(trdUserId, due)).ccommit
            _ <- eventStore.produceEvent(AccountDeletionEvent.ToSchedule(fthUserId, notYetDue)).ccommit

            result <- fs2.Stream
              .repeatEval {
                eventStore.consumeEvent().ccommit
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

trait PostgresAccountDeletionEventsStoreSpecContext extends DockerPostgresSuite:
  def cleanStorage: ConnectionIO[Unit] =
    sql"TRUNCATE TABLE auth.account_deletion_scheduled_events".update.run.void

  def fetchPendingEvents(): ConnectionIO[List[AccountDeletionEvent.Scheduled]] =
    sql"SELECT * FROM auth.account_deletion_scheduled_events".query[AccountDeletionEvent.Scheduled].to[List]
