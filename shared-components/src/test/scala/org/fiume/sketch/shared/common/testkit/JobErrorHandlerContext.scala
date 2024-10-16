package org.fiume.sketch.shared.common.testkit

import cats.effect.IO
import cats.implicits.*
import org.fiume.sketch.shared.common.jobs.JobErrorHandler

trait JobErrorHandlerContext:

  def makeJobErrorTracker(): IO[JobErrorHandler[IO] & Inspect] = IO.ref(0).map { state =>
    new JobErrorHandler[IO] with Inspect:
      override val handleJobError: Throwable => IO[Unit] =
        _ => state.update { _ |+| 1 }

      override def countHandledJobErrors(): IO[Int] = state.get
  }

  trait Inspect:
    def countHandledJobErrors(): IO[Int]
