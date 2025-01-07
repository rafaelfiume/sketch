package org.fiume.sketch.storage.auth.postgres

import cats.effect.IO
import cats.implicits.*
import doobie.ConnectionIO
import doobie.implicits.*
import munit.ScalaCheckEffectSuite
import org.fiume.sketch.shared.auth.UserId
import org.fiume.sketch.shared.auth.accounts.AccountDeletedNotification
import org.fiume.sketch.shared.auth.testkit.UserGens
import org.fiume.sketch.shared.common.events.{EventId, Recipient}
import org.fiume.sketch.shared.testkit.ClockContext
import org.fiume.sketch.shared.testkit.syntax.OptionSyntax.*
import org.fiume.sketch.storage.testkit.DockerPostgresSuite
import org.scalacheck.{Gen, ShrinkLowPriority}
import org.scalacheck.effect.PropF.forAllF

class PostgresAccountDeletedNotificationsStoreSpec
    extends ScalaCheckEffectSuite
    with ClockContext
    with PostgresAccountDeletedNotificationStoreSpecContext
    with ShrinkLowPriority:

  override def scalaCheckTestParameters = super.scalaCheckTestParameters.withMinSuccessfulTests(1)

  // Notes

  // Properties: No processing order guarantees are provided by the current implementation.

  // Performance: A extremelly simple benchmark assuming that the number of writes is roughly the same as the number of reads:
  // - consumed 50000 notifications in 127485 ms with _no_ indexing
  // - consumed 50000 notifications in 147354 ms with indexing on `recipient` column.
  // The reason for the indexing not improving performace could be that
  // there is only a handful possible values for a recipient, so the index is not selective enough.
  // "An index must be selective enough to reduce the number of disk lookups for it to be worth it." Source:
  // https://devcenter.heroku.com/articles/postgresql-indexes

  // override def munitIOTimeout = Duration(10, "m")

  test("consumes next event with exactly-once semantics and insures it is routed to the correct recipient"):
    forAllF { () =>
      will(cleanStorage) {
        // given
        val numUsers = 1000
        val recipients = List(Recipient("documents"), Recipient("projects"))
        val recipient = Gen.oneOf(recipients).sample.someOrFail
        val numNotifications = numUsers * recipients.size
        (PostgresAccountDeletedNotificationsStore.makeProducer[IO](),
         PostgresAccountDeletedNotificationsStore.makeConsumer[IO](recipient)
        ).tupled.use { case (producer, consumer) =>
          for
            // start <- Clock[IO].realTime.map(_.toMillis)
            notifications <- fs2.Stream
              .range(0, numUsers)
              .covary[IO]
              .parEvalMapUnbounded { _ =>
                val userId = UserGens.userIds.sample.someOrFail
                recipients.traverse { recipient =>
                  producer.produceEvent(AccountDeletedNotification.ToNotify(userId, recipient)).ccommit
                }
              }
              // .evalTap { eventId => IO.println(s"new event: $eventId") } // uncomment to debug
              .compile
              .toList
              .map(_.flatten)

            // when
            result <-
              fs2.Stream
                .range(0, numNotifications)
                .covary[IO]
                .parEvalMapUnorderedUnbounded { _ => consumer.consumeEvent().ccommit }
                // .evalTap { eventId => IO.println(s"consumed event: ${eventId}") } // uncomment to debug
                .unNone
                .compile
                .toList
            // end <- Clock[IO].realTime.map(_.toMillis)

            // then
            expectedResultSize = numNotifications / recipients.size
            expectedNotifications = notifications.filter(_.recipient == recipient)
            pendingNotifications <- fetchPendingEvents().ccommit
            expectedPendingNotifications = notifications.filterNot(_.recipient == recipient)
          // _ <- IO.println(s"consumed ${result.size} notifications in ${end - start} ms")
          yield
            assert(result.size == expectedResultSize, clue = s"expected $expectedResultSize notifications, got ${result.size}")
            assertEquals(result.toSet, expectedNotifications.toSet)
            assertEquals(pendingNotifications.toSet, expectedPendingNotifications.toSet)
        }
      }
    }

  // The larger the number of notifications sent back for processing, the greater the chances of delaying or blocking other notifications.
  // Ideally, failed notifications should be retried using exponential backoff with a maximum number of retries.
  test("notifications becomes available for reprocessing if its processing fails"):
    forAllF { () =>
      will(cleanStorage) {
        // given
        val numUsers = 1000
        val recipients = List(Recipient("documents"), Recipient("projects"))
        val recipient = Gen.oneOf(recipients).sample.someOrFail
        val numNotifications = numUsers * recipients.size
        val errorFrequency = 2
        (PostgresAccountDeletedNotificationsStore.makeProducer[IO](),
         PostgresAccountDeletedNotificationsStore.makeConsumer[IO](recipient)
        ).tupled.use { case (producer, consumer) =>
          for
            /*
             * If `Ref` seems overkill and that a simple `mutable.Set.empty[EventId]` would be sufficient,
             * consider that the latter is not concurrent-safe and makes the test flaky.
             */
            successfullyProcessedEventIdsRef <- IO.ref(Set.empty[EventId])
            atLeastOnceFailedEventIdsRef <- IO.ref(Set.empty[EventId])
            sentNotificationIds <- fs2.Stream
              .range(0, numUsers)
              .covary[IO]
              .parEvalMapUnbounded { _ =>
                val userId = UserGens.userIds.sample.someOrFail
                recipients.traverse { recipient =>
                  producer.produceEvent(AccountDeletedNotification.ToNotify(userId, recipient)).map(_.uuid).ccommit
                }
              }
              .compile
              .toList
              .map(_.flatten)

            // when
            result <-
              fs2.Stream
                .range(0, numNotifications)
                .covary[IO]
                .parEvalMapUnorderedUnbounded { i =>
                  consumer
                    .consumeEvent()
                    .flatMap { notification =>
                      if i % errorFrequency == 0 then
                        lift {
                          atLeastOnceFailedEventIdsRef.update(s => notification.fold(s)(s + _.uuid)) *>
                            RuntimeException(s"failed: ${notification.map(_.uuid)}").raiseError
                        }
                      else
                        lift { successfullyProcessedEventIdsRef.update(s => notification.fold(s)(s + _.uuid)) } *>
                          notification.pure[ConnectionIO]
                    }
                    .ccommit
                    .handleErrorWith { _ => none.pure[IO] }
                }
                .map(_.map(_.uuid))
                .unNone
                .compile
                .toList

            // then
            atLeastOnceFailedNotificationIds <- atLeastOnceFailedEventIdsRef.get
            successfullyProcessedNotificationIds <- successfullyProcessedEventIdsRef.get
            pendingNotificationIds <- fetchPendingEvents().ccommit.map(_.toSet.map(_.uuid))
            expectedPendingNotificationIds = sentNotificationIds.toSet -- successfullyProcessedNotificationIds
          yield
            assert(
              atLeastOnceFailedNotificationIds.find(successfullyProcessedNotificationIds.contains).isDefined,
              clue = "retries processing of failed notifications"
            )
            assertEquals(pendingNotificationIds, expectedPendingNotificationIds)
        }
      }
    }

trait PostgresAccountDeletedNotificationStoreSpecContext extends DockerPostgresSuite:
  def cleanStorage: ConnectionIO[Unit] =
    sql"TRUNCATE TABLE auth.account_deleted_notifications".update.run.void

  def fetchPendingEvents(): ConnectionIO[List[AccountDeletedNotification.Notified]] =
    sql"SELECT * FROM auth.account_deleted_notifications".query[AccountDeletedNotification.Notified].to[List]

  import doobie.Read
  import org.fiume.sketch.storage.auth.postgres.DatabaseCodecs.given
  given Read[AccountDeletedNotification.Notified] = Read[(EventId, UserId, Recipient)].map { case (eventId, userId, recipient) =>
    AccountDeletedNotification.notified(eventId, userId, recipient)
  }
