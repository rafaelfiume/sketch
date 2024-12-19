package org.fiume.sketch.auth.accounts.jobs

import cats.effect.IO
import cats.implicits.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.fiume.sketch.shared.auth.accounts.{Account, AccountState}
import org.fiume.sketch.shared.auth.accounts.jobs.AccountDeletionEvent
import org.fiume.sketch.shared.auth.testkit.AccountGens.given
import org.fiume.sketch.shared.auth.testkit.ScheduledAccountDeletionEventFlowContext.EventConsumerContext
import org.fiume.sketch.shared.auth.testkit.UsersStoreContext
import org.fiume.sketch.shared.common.jobs.JobId
import org.fiume.sketch.shared.common.testkit.JobGens.given
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
    forAllF { (account: Account, jobId: JobId, permanentDeletionAt: Instant) =>
      for
        store <- makeUsersStoreForAccount(account.copy(state = AccountState.SoftDeleted(Instant.now())))
        eventConsumer <- makeEventConsumer(nextEvent = AccountDeletionEvent.Scheduled(jobId, account.uuid, permanentDeletionAt))
        job = ScheduledAccountDeletionJob.make(eventConsumer, store)

        result <- job.run()

        _ <- assertIO(store.fetchAccount(account.uuid), none)
      yield assertEquals(result, (jobId, account.uuid).some)
    }

  test("skips processing if no permanent deletions are scheduled"):
    for
      store <- makeEmptyUsersStore()
      eventConsumer <- makeEmptyEventConsumer()
      job = ScheduledAccountDeletionJob.make(eventConsumer, store)

      result <- job.run()
//
    yield assertEquals(result, none)
