package org.fiume.sketch.app

import cats.effect.{IO, IOApp}
import org.slf4j.LoggerFactory

object Main extends IOApp.Simple:

  private val log = LoggerFactory.getLogger(Main.getClass)

  Thread.setDefaultUncaughtExceptionHandler(
    new Thread.UncaughtExceptionHandler():
      def uncaughtException(t: Thread, e: Throwable): Unit =
        log.error("Unexpected exception: service is crashing", e)
        System.exit(1)
  )

  override def run: IO[Unit] = Server.run[IO]
