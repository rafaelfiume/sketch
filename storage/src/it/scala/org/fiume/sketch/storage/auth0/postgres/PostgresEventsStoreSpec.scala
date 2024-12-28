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
import org.fiume.sketch.shared.common.events.EventId
import org.fiume.sketch.shared.testkit.ClockContext
import org.fiume.sketch.shared.testkit.syntax.OptionSyntax.*
import org.fiume.sketch.storage.testkit.DockerPostgresSuite
import org.scalacheck.ShrinkLowPriority
import org.scalacheck.effect.PropF.forAllF

import java.time.Instant
import java.util.UUID

class PostgresEventsStoreSpec
    extends ScalaCheckEffectSuite
    with ClockContext
    with PostgresEventStoreSpecContext
    with ShrinkLowPriority:

  override def scalaCheckTestParameters = super.scalaCheckTestParameters.withMinSuccessfulTests(10)

  test("consumes next event"): // TODO Is queue the correct terminology here?
    forAllF { () =>
      will(cleanStorage) {
        PostgresEventsStore.make[IO]().use { eventsStore =>
          // given
          val numEvents = 1000
          val due = Instant.now()
          for
            delayedMessages <- fs2.Stream
              .range(0, numEvents)
              .covary[IO]
              .parEvalMapUnbounded { _ =>
                val userId = UserGens.userIds.sample.someOrFail
                eventsStore.produceEvent(AccountDeletionEvent.Unscheduled(userId, due)).ccommit
              }
              // .evalTap { eventId => IO.println(s"new event: $eventId") } // uncomment to debug
              .compile
              .toList

            // when
            result <-
              fs2.Stream
                .range(0, numEvents)
                .covary[IO]
                .parEvalMapUnorderedUnbounded { _ => eventsStore.consumeEvent().ccommit }
                // .evalTap { eventId => IO.println(s"consumed event: ${eventId}") } // uncomment to debug
                .unNone
                .compile
                .toList

          // then
          yield
            assert(result.size == numEvents, clue = s"Expected $numEvents events, got ${result.size}")
            assertEquals(result.toSet, delayedMessages.toSet)
        }
      }
    }

  // The larger the number of events sent back for processing, the greater the chances of delaying or blocking other events.
  // Ideally, failed events should be retried using exponential backoff with a maximum number of retries.
  test("event becomes available for reprocessing if its processing fails"):
    forAllF { () =>
      will(cleanStorage) {
        PostgresEventsStore.make[IO]().use { eventsStore =>
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
            queuedEventIds <- fs2.Stream
              .range(0, numEvents)
              .covary[IO]
              .parEvalMapUnbounded { _ =>
                val userId = UserGens.userIds.sample.someOrFail
                eventsStore.produceEvent(AccountDeletionEvent.Unscheduled(userId, due)).map(_.uuid).ccommit
              }
              .compile
              .toList

            // when
            result <-
              fs2.Stream
                .range(0, numEvents)
                .covary[IO]
                .parEvalMapUnorderedUnbounded { i =>
                  eventsStore
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
            expectedPendingEventIds = queuedEventIds.toSet -- successfullyProcessedEventIds
          yield
            assert(
              atLeastOnceFailedEventIds.find(successfullyProcessedEventIds.contains).isDefined,
              clue = "retries processing of failed events"
            )
            assertEquals(pendingEventIds, expectedPendingEventIds)
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

// Note: no processing order guarantees are provided by the current implementation

trait PostgresEventStoreSpecContext extends DockerPostgresSuite:
  def cleanStorage: ConnectionIO[Unit] =
    sql"TRUNCATE TABLE auth.account_permanent_deletion_delayed_messages".update.run.void

  def fetchPendingEvents(): ConnectionIO[List[AccountDeletionEvent.Scheduled]] =
    sql"SELECT * FROM auth.account_permanent_deletion_delayed_messages".query[AccountDeletionEvent.Scheduled].to[List]

  import doobie.{Meta, Read}
  import org.fiume.sketch.storage.auth.postgres.DatabaseCodecs.given
  import doobie.postgres.implicits.*
  import org.fiume.sketch.shared.common.events.EventId
  given Meta[UserId] = Meta[UUID].timap(UserId(_))(_.value)
  given Read[AccountDeletionEvent.Scheduled] = Read[(EventId, UserId, Instant)].map {
    case (eventId, userId, permanentDeletionAt) =>
      AccountDeletionEvent.Scheduled(eventId, userId, permanentDeletionAt)
  }
