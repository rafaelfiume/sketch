package org.fiume.sketch.shared.common.testkit

import org.fiume.sketch.shared.common.events.EventId
import org.scalacheck.{Arbitrary, Gen}

object EventGens:

  given Arbitrary[EventId] = Arbitrary(eventIds)
  def eventIds: Gen[EventId] = Gen.uuid.map(EventId(_)) :| "EventId"
