package org.fiume.sketch.shared.testkit

import cats.Applicative
import cats.effect.{Clock, IO}
import cats.implicits.*

import java.time.{Instant, ZoneOffset, ZonedDateTime}
import scala.concurrent.duration.*

trait ClockContext:

  def makeAnytime(): Clock[IO] = makeFrozenTime(ZonedDateTime.now(ZoneOffset.UTC))

  def makeFrozenTime(zdt: ZonedDateTime): Clock[IO] = makeFrozenTime(zdt.toInstant)

  def makeFrozenTime(instant: Instant): Clock[IO] =
    new Clock[IO]:

      override def applicative: cats.Applicative[IO] = Applicative.apply

      override def realTime: IO[FiniteDuration] =
        instant.toEpochMilli().milliseconds.pure[IO]

      override def monotonic: IO[FiniteDuration] = IO
        // this seems correct?
        .delay(Instant.now().getEpochSecond * 1000000000L + ZonedDateTime.now().getNano)
        .map(_.nanos)
