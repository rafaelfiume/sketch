package org.fiume.sketch.app

import cats.effect.{ExitCode, IO, IOApp}
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.slf4j.LoggerFactory

import java.security.Security

object Main extends IOApp:

  private val log = LoggerFactory.getLogger(Main.getClass)

  Thread.setDefaultUncaughtExceptionHandler(
    new Thread.UncaughtExceptionHandler():
      def uncaughtException(t: Thread, e: Throwable): Unit =
        log.error("Unexpected exception: service is crashing", e)
        System.exit(1)
  )

  override protected def blockedThreadDetectionEnabled = true

  override def run(args: List[String]): IO[ExitCode] =
    IO.delay { Security.addProvider(new BouncyCastleProvider()) } *>
      Server.run[IO]()
