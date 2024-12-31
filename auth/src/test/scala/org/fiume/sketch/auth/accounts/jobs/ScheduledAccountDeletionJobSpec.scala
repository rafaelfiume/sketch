package org.fiume.sketch.auth.accounts.jobs

import cats.effect.IO
import cats.implicits.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.fiume.sketch.shared.auth.UserId
import org.fiume.sketch.shared.auth.accounts.{Account, AccountDeletedNotification, AccountDeletionEvent, AccountState}
import org.fiume.sketch.shared.auth.accounts.AccountDeletedNotification.ToNotify
import org.fiume.sketch.shared.auth.accounts.AccountDeletionEvent.ToSchedule
import org.fiume.sketch.shared.auth.testkit.AccountGens.given
import org.fiume.sketch.shared.auth.testkit.EventsFlowContext.{EventConsumerContext, EventProducerContext}
import org.fiume.sketch.shared.auth.testkit.UsersStoreContext
import org.fiume.sketch.shared.common.events.EventId
import org.fiume.sketch.shared.common.testkit.EventGens.given
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

  test("deletes the account when a permanent deletion is scheduled"):
    forAllF { (account: Account, eventId: EventId, permanentDeletionAt: Instant) =>
      for
        store <- makeUsersStoreForAccount(account.copy(state = AccountState.SoftDeleted(Instant.now())))
        eventConsumer <- makeEventConsumer(nextEvent = AccountDeletionEvent.scheduled(eventId, account.uuid, permanentDeletionAt))
        notificationProducer <- makeAccountDeletedNotificationProducer()
        job = ScheduledAccountDeletionJob.make(eventConsumer, notificationProducer, store)

        result <- job.run()

        // TODO Check notification produced
        _ <- assertIO(store.fetchAccount(account.uuid), none)
      yield assertEquals(result, (eventId, account.uuid).some)
    }

  test("skips processing if no permanent deletions are scheduled"):
    for
      store <- makeEmptyUsersStore()
      eventConsumer <- makeEmptyEventConsumer()
      notificationProducer <- makeAccountDeletedNotificationProducer()
      job = ScheduledAccountDeletionJob.make(eventConsumer, notificationProducer, store)

      result <- job.run()
//
    // TODO Check no notification produced
    yield assertEquals(result, none)

trait ScheduledAccountDeletionJobSpecContext extends EventProducerContext[ToNotify]:
  def makeAccountDeletedNotificationProducer() = makeEventProducer(
    enrich = (event, eventId) => AccountDeletedNotification.notified(eventId, event.userId, event.target)
  )
