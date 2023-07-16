package org.fiume.sketch.shared.test

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

      override def monotonic: IO[FiniteDuration] = IO
        // does this work?
        .delay(ZonedDateTime.now().toInstant.getEpochSecond * 1000000000L + ZonedDateTime.now().getNano)
        .map(FiniteDuration(_, TimeUnit.NANOSECONDS))

  def anyTime: Clock[IO] = makeFrozenTime(ZonedDateTime.now(ZoneOffset.UTC))
