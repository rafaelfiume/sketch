package org.fiume.sketch.app

import cats.effect.{ExitCode, IO, IOApp}
import org.slf4j.LoggerFactory

object Main extends IOApp:

  private val log = LoggerFactory.getLogger(Main.getClass)

  Thread.setDefaultUncaughtExceptionHandler(
    new Thread.UncaughtExceptionHandler():
      def uncaughtException(t: Thread, e: Throwable): Unit =
        log.error("Unexpected exception: service is crashing", e)
        System.exit(1)
  )

  override protected def blockedThreadDetectionEnabled = true

  override def run(args: List[String]): IO[ExitCode] = Server.run[IO]
