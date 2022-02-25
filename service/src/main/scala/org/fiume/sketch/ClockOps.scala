package org.fiume.sketch

import cats.Functor
import cats.effect.Clock
import cats.implicits.*

import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime

object ClockOps:
  extension [F[_]: Functor](clock: Clock[F])
    def getNow(): F[ZonedDateTime] =
      clock.realTimeInstant.map { instant => ZonedDateTime.ofInstant(instant, UTC) }
