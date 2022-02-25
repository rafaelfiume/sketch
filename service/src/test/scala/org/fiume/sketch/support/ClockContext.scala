package org.fiume.sketch.support

import cats.Applicative
import cats.effect.{Clock, IO}
import cats.implicits.*

import java.time.{ZoneOffset, ZonedDateTime}
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

trait ClockContext:

  def makeFrozenTime(frozen: ZonedDateTime): Clock[IO] =
    new Clock[IO]:

      override def realTime: IO[FiniteDuration] =
        FiniteDuration(frozen.toInstant().toEpochMilli(), TimeUnit.MILLISECONDS).pure[IO]

      override def applicative: cats.Applicative[IO] = Applicative.apply

      // As far as I know, Java 8 doesn't provide Instant w/ nanoseconds resolution
      override def monotonic: IO[FiniteDuration] = ???

  def anyTime: Clock[IO] = makeFrozenTime(ZonedDateTime.now(ZoneOffset.UTC))
