package org.fiume.sketch.shared.jobs

import cats.effect.IO
import cats.effect.kernel.Ref
import cats.implicits.*
import munit.CatsEffectSuite
import org.fiume.sketch.shared.jobs.PeriodicJob.JobErrorHandler

import scala.concurrent.duration.*

class PeriodicJobSpec extends CatsEffectSuite with JobErrorHandlerContext:

  test("runs job periodically, even after encountering errors"):
    // given
    def makeBrokenJob(): IO[(Ref[IO, Int], Job[IO, Unit])] = Ref.of[IO, Int](0).map { counter =>
      counter -> JobWrapper(
        effect = counter.flatModify { i =>
          val jobNumber = i + 1
          if jobNumber % 2 == 0 then (jobNumber, RuntimeException("boom").raiseError)
          else (jobNumber, IO.unit)
        },
        description = "broken job"
      )
    }
    for
      (jobsCounter, brokenJob) <- makeBrokenJob()
      jobErrorTracker <- makeJobErrorTracker()
      period = 50.millis
      pipelineDuration = 170.millis

      // when
      _ <- PeriodicJob
        .make(period, brokenJob, jobErrorTracker)
        .interruptAfter(pipelineDuration)
        .compile
        .drain

      // then
      totalJobsRun <- jobsCounter.get
      expectedTotalJobsRun = (pipelineDuration / period).toInt
      totalErrorsHandled <- jobErrorTracker.countJobErrors()
      expectedTotalErrorsHandled = totalJobsRun / 2
    yield
      assert(
        totalJobsRun == expectedTotalJobsRun,
        clue = s"Expected $expectedTotalJobsRun jobs to run, but $totalJobsRun ran"
      )
      assert(
        totalErrorsHandled == expectedTotalErrorsHandled,
        clue = s"Expected $expectedTotalErrorsHandled errors, but $totalErrorsHandled occurred"
      )

  test("stops running jobs when interrupted"):
    for
      jobCounter <- Ref.of[IO, Int](0)
      job = JobWrapper(jobCounter.update(_ + 1), "increments a counter")
      fiber <- PeriodicJob
        .makeWithDefaultJobErrorHandler(50.millis, job)
        .interruptAfter(170.millis)
        .compile
        .drain
        .start
      _ <- IO.sleep(110.millis) // let the jobs run fow a while

      numberOfJobsRun <- jobCounter.get
      _ <- if numberOfJobsRun == 2 then fiber.cancel else IO.unit
      _ <- fiber.join
//
    yield assert(numberOfJobsRun == 2, clue = s"Expected 2 jobs to run, but $numberOfJobsRun ran")

trait JobErrorHandlerContext:

  def makeJobErrorTracker(): IO[JobErrorHandler[IO, Unit] & Inspect] = Ref.of[IO, Int](0).map { state =>
    new JobErrorHandler[IO, Unit] with Inspect:
      override val handleJobError: Throwable => IO[Option[Unit]] =
        _ => state.update { _ |+| 1 }.as(none[Unit])

      override def countJobErrors(): IO[Int] = state.get
  }

  trait Inspect:
    def countJobErrors(): IO[Int]
