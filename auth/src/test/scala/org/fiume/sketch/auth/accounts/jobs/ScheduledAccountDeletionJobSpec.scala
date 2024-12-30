package org.fiume.sketch.auth.accounts.jobs

import cats.effect.IO
import cats.implicits.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.fiume.sketch.shared.auth.accounts.{Account, AccountDeletionEvent, AccountState}
import org.fiume.sketch.shared.auth.testkit.AccountGens.given
import org.fiume.sketch.shared.auth.testkit.ScheduledAccountDeletionEventFlowContext.EventConsumerContext
import org.fiume.sketch.shared.auth.testkit.UsersStoreContext
import org.fiume.sketch.shared.common.events.EventId
import org.fiume.sketch.shared.common.testkit.EventGens.given
import org.scalacheck.ShrinkLowPriority
import org.scalacheck.effect.PropF.forAllF

import java.time.Instant

class ScheduledAccountDeletionJobSpec
    extends CatsEffectSuite
    with ScalaCheckEffectSuite
    with EventConsumerContext
    with UsersStoreContext
    with ShrinkLowPriority:

  override def scalaCheckTestParameters = super.scalaCheckTestParameters.withMinSuccessfulTests(10)

  test("deletes the account when a permanent deletion is scheduled"):
    forAllF { (account: Account, eventId: EventId, permanentDeletionAt: Instant) =>
      for
        store <- makeUsersStoreForAccount(account.copy(state = AccountState.SoftDeleted(Instant.now())))
        eventConsumer <- makeEventConsumer(nextEvent = AccountDeletionEvent.scheduled(eventId, account.uuid, permanentDeletionAt))
        job = ScheduledAccountDeletionJob.make(eventConsumer, store)

        result <- job.run()

        _ <- assertIO(store.fetchAccount(account.uuid), none)
      yield assertEquals(result, (eventId, account.uuid).some)
    }

  test("skips processing if no permanent deletions are scheduled"):
    for
      store <- makeEmptyUsersStore()
      eventConsumer <- makeEmptyEventConsumer()
      job = ScheduledAccountDeletionJob.make(eventConsumer, store)

      result <- job.run()
//
    yield assertEquals(result, none)
