package org.fiume.sketch.app

import cats.effect.{IO, IOApp}
import org.slf4j.LoggerFactory

object Main extends IOApp.Simple:

  private val log = LoggerFactory.getLogger(Main.getClass)

  Thread.setDefaultUncaughtExceptionHandler(
    new Thread.UncaughtExceptionHandler():
      def uncaughtException(t: Thread, e: Throwable): Unit =
        log.error(s"Unexpected unhandled exception in a pure functional code likely to be caused by a bug: ${e.getMessage}", e)
        System.exit(13)
  )

  override def run: IO[Unit] = Server.run[IO]
