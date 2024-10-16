package org.fiume.sketch.shared.common.jobs

import cats.effect.IO
import munit.CatsEffectSuite
import org.fiume.sketch.shared.common.jobs.syntax.JobErrorHandlerSyntax.*
import org.fiume.sketch.shared.common.testkit.JobErrorHandlerContext

class JobErrorHandlerSpec extends CatsEffectSuite with JobErrorHandlerContext:

  test("composes multiple job error handlers") {
    for
      fstHandler <- makeJobErrorTracker()
      sndHandler <- makeJobErrorTracker()
      trdHandler <- makeJobErrorTracker()
      fthHandler <- makeJobErrorTracker()
      fihHandler <- makeJobErrorTracker() // not combined

      combinedHandlers = fstHandler.combine(sndHandler).combineAll(List(trdHandler, fthHandler))
      _ <- combinedHandlers.handleJobError(new RuntimeException("boom"))

      _ <- fstHandler.countHandledJobErrors().map(count => assert(count == 1))
      _ <- sndHandler.countHandledJobErrors().map(count => assert(count == 1))
      _ <- trdHandler.countHandledJobErrors().map(count => assert(count == 1))
      _ <- fthHandler.countHandledJobErrors().map(count => assert(count == 1))
      _ <- fihHandler.countHandledJobErrors().map(count => assert(count == 0))
    yield ()
  }
