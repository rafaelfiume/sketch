package org.fiume.sketch.shared.app.algebras

import cats.Functor
import cats.effect.Clock
import cats.implicits.*

import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime

object ClockOps: // there might be a better package to Ops than `algebras`?
  extension [F[_]: Functor](clock: Clock[F])
    def getNow(): F[ZonedDateTime] =
      clock.realTimeInstant.map { instant => ZonedDateTime.ofInstant(instant, UTC) }
