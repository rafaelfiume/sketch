package org.fiume.sketch.shared.jobs

import cats.effect.IO
import cats.effect.kernel.Ref
import munit.CatsEffectSuite

import scala.concurrent.duration.*

class PeriodicJobSpec extends CatsEffectSuite:
  test("runs a job periodically"):
    for
      counter <- Ref.of[IO, Int](0)
      _ <- PeriodicJob.make(counter.update(_ + 1), 50.millis).interruptAfter(170.millis).compile.drain
      jobsRun <- counter.get
    yield assert(jobsRun == 3, clue = s"Expected at least 3 jobs to run, but only $jobsRun ran")
