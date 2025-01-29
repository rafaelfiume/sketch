package org.fiume.sketch.users

import cats.effect.IO
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.fiume.sketch.shared.auth.accounts.AccountDeletedNotification
import org.fiume.sketch.shared.auth.accounts.AccountDeletedNotification.ToNotify
import org.fiume.sketch.shared.auth.testkit.EventsFlowContext.EventConsumerContext
import org.fiume.sketch.shared.common.events.{EventId, Recipient}
import org.fiume.sketch.shared.common.testkit.EventGens.given
import org.fiume.sketch.shared.domain.documents.DocumentWithIdAndStream
import org.fiume.sketch.shared.domain.testkit.DocumentsGens.given
import org.fiume.sketch.shared.domain.testkit.DocumentsStoreContext
import org.fiume.sketch.shared.testkit.syntax.OptionSyntax.someOrFail
import org.fiume.sketch.users.UserDataDeletionJob.JobReport
import org.scalacheck.ShrinkLowPriority
import org.scalacheck.effect.PropF.forAllF

class UserDataDeletionJobSpec
    extends CatsEffectSuite
    with ScalaCheckEffectSuite
    with EventConsumerContext[ToNotify]
    with DocumentsStoreContext
    with ShrinkLowPriority:

  override def scalaCheckTestParameters = super.scalaCheckTestParameters.withMinSuccessfulTests(10)

  test("deletes user data when a user's account notification is received"):
    forAllF { (triggeringEventId: EventId, fstDoc: DocumentWithIdAndStream[IO], sndDoc: DocumentWithIdAndStream[IO]) =>
      val userId = sndDoc.metadata.ownerId
      for
        eventConsumer <- makeEventConsumer(nextEvent =
          AccountDeletedNotification.notified(triggeringEventId, userId, Recipient("job-pbt"))
        )
        store <- makeDocumentsStore(state = fstDoc, sndDoc)
        job = UserDataDeletionJob.make(eventConsumer, store)

        result <- job.run().map(_.someOrFail)
//
      yield assertEquals(result, JobReport(triggeringEventId, userId, List(sndDoc.uuid)))
    }

  test("skips processing if there are no account deletion notifications"):
    forAllF { (fstDoc: DocumentWithIdAndStream[IO], sndDoc: DocumentWithIdAndStream[IO]) =>
      for
        eventConsumer <- makeEmptyEventConsumer()
        store <- makeDocumentsStore(state = fstDoc, sndDoc)
        job = UserDataDeletionJob.make(eventConsumer, store)

        result <- job.run()
//
      yield assertEquals(result, None)
    }
