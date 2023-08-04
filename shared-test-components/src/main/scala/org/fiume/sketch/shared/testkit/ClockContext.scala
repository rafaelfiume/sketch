package org.fiume.sketch.shared.testkit

import cats.Applicative
import cats.effect.{Clock, IO}
import cats.implicits.*

import java.time.{ZoneOffset, ZonedDateTime}
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.*

trait ClockContext:

  def makeFrozenTime(frozen: ZonedDateTime): Clock[IO] =
    new Clock[IO]:

      override def realTime: IO[FiniteDuration] =
        frozen.toInstant().toEpochMilli().milliseconds.pure[IO]

      override def applicative: cats.Applicative[IO] = Applicative.apply

      override def monotonic: IO[FiniteDuration] = IO
        // does this work?
        .delay(ZonedDateTime.now().toInstant.getEpochSecond * 1000000000L + ZonedDateTime.now().getNano)
        .map(_.nanos)

  def anyTime: Clock[IO] = makeFrozenTime(ZonedDateTime.now(ZoneOffset.UTC))
