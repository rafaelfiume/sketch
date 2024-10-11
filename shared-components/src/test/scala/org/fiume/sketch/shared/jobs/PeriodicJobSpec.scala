package org.fiume.sketch.shared.jobs

import cats.effect.{IO, Ref}
import cats.implicits.*
import munit.CatsEffectSuite
import org.fiume.sketch.shared.testkit.JobErrorHandlerContext

import scala.concurrent.duration.*

class PeriodicJobSpec extends CatsEffectSuite with JobErrorHandlerContext:

  test("runs job periodically, even after encountering errors"):
    // given
    def makeBrokenJob(): IO[(Ref[IO, Int], Job[IO, Int])] = IO.ref(0).map { counter =>
      counter -> JobWrapper(
        effect = counter.flatModify { i =>
          val jobNumber = i + 1
          if jobNumber % 2 == 0 then (jobNumber, RuntimeException("boom").raiseError)
          else (jobNumber, jobNumber.pure[IO])
        },
        description = "broken job"
      )
    }
    for
      (jobsCounter, brokenJob) <- makeBrokenJob()
      jobErrorTracker <- makeJobErrorTracker()
      period = 50.millis
      pipelineDuration = 180.millis // this test can be sucesptible to timing issues

      // when
      result <- PeriodicJob
        .make(period, brokenJob, jobErrorTracker)
        .interruptAfter(pipelineDuration)
        .compile
        .toList

      // then
      totalJobsRun <- jobsCounter.get
      expectedTotalJobsRun = (pipelineDuration / period).toInt
      totalErrorsHandled <- jobErrorTracker.countHandledJobErrors()
      expectedTotalErrorsHandled = totalJobsRun / 2
      expectedEmittedOutput = (1 to totalJobsRun by 2).toList
    yield
      assert(
        totalJobsRun == expectedTotalJobsRun,
        clue = s"Expected $expectedTotalJobsRun jobs to run, but $totalJobsRun ran"
      )
      assert(
        totalErrorsHandled == expectedTotalErrorsHandled,
        clue = s"Expected $expectedTotalErrorsHandled errors, but $totalErrorsHandled occurred"
      )
      assertEquals(result, expectedEmittedOutput)

  test("stops running jobs when interrupted"):
    for
      jobCounter <- IO.ref(0)
      job = JobWrapper(jobCounter.update(_ + 1), "increments a counter")
      fiber <- PeriodicJob
        .makeWithDefaultJobErrorHandler(50.millis, job)
        .interruptAfter(190.millis)
        .compile
        .drain
        .start
      _ <- IO.sleep(110.millis) // let the jobs run fow a while

      _ <- fiber.cancel
      _ <- IO.sleep(90.millis)
      numberOfJobsRun <- jobCounter.get

//
    yield assert(numberOfJobsRun == 2, clue = s"Expected 2 jobs to run, but $numberOfJobsRun ran")
