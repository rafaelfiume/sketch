package org.fiume.sketch.shared.common.testkit

import org.fiume.sketch.shared.common.events.{EventId, Recipient}
import org.scalacheck.{Arbitrary, Gen}

object EventGens:

  given Arbitrary[EventId] = Arbitrary(eventIds)
  def eventIds: Gen[EventId] = Gen.uuid.map(EventId(_)) :| "EventId"

  given Arbitrary[Recipient] = Arbitrary(recipients)
  def recipients: Gen[Recipient] =
    Gen.oneOf(List(Recipient("documents"), Recipient("projects"), Recipient("access-control"))) :| "Recipient"
