package org.fiume.sketch.shared.testkit

import cats.effect.{IO, Ref}
import cats.implicits.*
import org.fiume.sketch.shared.jobs.JobErrorHandler

trait JobErrorHandlerContext:

  def makeJobErrorTracker(): IO[JobErrorHandler[IO] & Inspect] = Ref.of[IO, Int](0).map { state =>
    new JobErrorHandler[IO] with Inspect:
      override val handleJobError: Throwable => IO[Unit] =
        _ => state.update { _ |+| 1 }

      override def countHandledJobErrors(): IO[Int] = state.get
  }

  trait Inspect:
    def countHandledJobErrors(): IO[Int]
