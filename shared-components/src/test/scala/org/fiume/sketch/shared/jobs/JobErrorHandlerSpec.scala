package org.fiume.sketch.shared.jobs

import cats.effect.IO
import munit.CatsEffectSuite
import org.fiume.sketch.shared.jobs.JobErrorHandler.syntax.*
import org.fiume.sketch.shared.testkit.JobErrorHandlerContext

class JobErrorHandlerSpec extends CatsEffectSuite with JobErrorHandlerContext:

  test("composes multiple job error handlers") {
    for
      fstHandler <- makeJobErrorTracker()
      sndHandler <- makeJobErrorTracker()
      trdHandler <- makeJobErrorTracker()
      fthHandler <- makeJobErrorTracker()
      fihHandler <- makeJobErrorTracker() // not combined

      composedHandler = fstHandler.compose(sndHandler).composeAll(List(trdHandler, fthHandler))
      _ <- composedHandler.handleJobError(new RuntimeException("boom"))

      _ <- fstHandler.countHandledJobErrors().map(count => assert(count == 1))
      _ <- sndHandler.countHandledJobErrors().map(count => assert(count == 1))
      _ <- trdHandler.countHandledJobErrors().map(count => assert(count == 1))
      _ <- fthHandler.countHandledJobErrors().map(count => assert(count == 1))
      _ <- fihHandler.countHandledJobErrors().map(count => assert(count == 0))
    yield ()
  }
