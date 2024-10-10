package org.fiume.sketch.app

import cats.effect.{IO, IOApp}
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.slf4j.LoggerFactory

import java.security.Security

object Main extends IOApp.Simple:

  private val log = LoggerFactory.getLogger(Main.getClass)

  Thread.setDefaultUncaughtExceptionHandler(
    new Thread.UncaughtExceptionHandler():
      def uncaughtException(t: Thread, e: Throwable): Unit =
        log.error("Unexpected exception: service is crashing", e)
        System.exit(1)
  )

  // See https://typelevel.org/cats-effect/docs/core/starvation-and-tuning#blocking-tasks
  // override protected def blockedThreadDetectionEnabled = true

  override def run: IO[Unit] =
    IO.delay { Security.addProvider(new BouncyCastleProvider()) } *>
      Server.run[IO]()
