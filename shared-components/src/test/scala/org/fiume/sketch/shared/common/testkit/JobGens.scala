package org.fiume.sketch.shared.common.testkit

import org.fiume.sketch.shared.common.jobs.JobId
import org.scalacheck.{Arbitrary, Gen}

object JobGens:

  given Arbitrary[JobId] = Arbitrary(jobIds)
  def jobIds: Gen[JobId] = Gen.uuid.map(JobId(_)) :| "JobId"
