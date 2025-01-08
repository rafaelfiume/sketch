package org.fiume.sketch.auth.accounts.jobs

import cats.effect.IO
import cats.implicits.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.fiume.sketch.auth.config.Dynamic.RecipientsKey
import org.fiume.sketch.auth.config.Dynamic.given
import org.fiume.sketch.shared.auth.UserId
import org.fiume.sketch.shared.auth.accounts.{Account, AccountDeletedNotification, AccountDeletionEvent, AccountState}
import org.fiume.sketch.shared.auth.accounts.AccountDeletedNotification.ToNotify
import org.fiume.sketch.shared.auth.accounts.AccountDeletionEvent.ToSchedule
import org.fiume.sketch.shared.auth.testkit.AccountGens.given
import org.fiume.sketch.shared.auth.testkit.EventsFlowContext.{EventConsumerContext, EventProducerContext}
import org.fiume.sketch.shared.auth.testkit.UsersStoreContext
import org.fiume.sketch.shared.common.config.InMemoryDynamicConfig
import org.fiume.sketch.shared.common.events.{EventId, Recipient}
import org.fiume.sketch.shared.common.testkit.EventGens.given
import org.fiume.sketch.shared.testkit.syntax.OptionSyntax.someOrFail
import org.scalacheck.ShrinkLowPriority
import org.scalacheck.effect.PropF.forAllF

import java.time.Instant

class ScheduledAccountDeletionJobSpec
    extends CatsEffectSuite
    with ScalaCheckEffectSuite
    with EventConsumerContext[ToSchedule]
    with UsersStoreContext
    with ScheduledAccountDeletionJobSpecContext
    with ShrinkLowPriority:

  override def scalaCheckTestParameters = super.scalaCheckTestParameters.withMinSuccessfulTests(10)

  test("deletes the account when a permanent deletion is scheduled and recipients are defined"):
    forAllF { (account: Account, triggeringEventId: EventId, permanentDeletionAt: Instant, recipients: Set[Recipient]) =>
      for
        dynamicConfig <- InMemoryDynamicConfig.makeWithKvPair[IO, IO, RecipientsKey.type, Set[Recipient]](
          key = RecipientsKey,
          value = recipients
        )
        store <- makeUsersStoreForAccount(account.copy(state = AccountState.SoftDeleted(Instant.now())))
        eventConsumer <- makeEventConsumer(nextEvent =
          AccountDeletionEvent.scheduled(triggeringEventId, account.uuid, permanentDeletionAt)
        )
        notificationProducer <- makeAccountDeletedNotificationProducer()
        job = ScheduledAccountDeletionJob.make(eventConsumer, notificationProducer, store, dynamicConfig)

        result <- job.run().map(_.someOrFail)

        totalSentNotifications <- notificationProducer.totalSent()
        _ <- assertIO(store.fetchAccount(account.uuid), none)
      yield
        assertEquals(result.triggeringEventId, triggeringEventId)
        assertEquals(result.deletedUserId, account.uuid)
        assertEquals(result.notificationsSent.toSet.map(_.recipient), recipients)
        assert(totalSentNotifications == recipients.size,
               clue = "number of fired fired notifications should equal targeted recipients"
        )
    }

  test("skips processing if no permanent deletions are scheduled"):
    forAllF { (recipients: Set[Recipient]) =>
      for
        store <- makeEmptyUsersStore()
        eventConsumer <- makeEmptyEventConsumer()
        notificationProducer <- makeAccountDeletedNotificationProducer()
        dynamicConfig <- InMemoryDynamicConfig.makeWithKvPair[IO, IO, RecipientsKey.type, Set[Recipient]](
          key = RecipientsKey,
          value = recipients
        )
        job = ScheduledAccountDeletionJob.make(eventConsumer, notificationProducer, store, dynamicConfig)

        result <- job.run()
//
        totalSentNotifications <- notificationProducer.totalSent()
      yield
        assertEquals(result, none)
        assert(totalSentNotifications == 0, clue = "no notifications should be fired when there is no triggering event")
    }

trait ScheduledAccountDeletionJobSpecContext extends EventProducerContext[ToNotify]:
  def makeAccountDeletedNotificationProducer() = makeEventProducer(
    enrich = (event, eventId) => AccountDeletedNotification.notified(eventId, event.userId, event.recipient)
  )
