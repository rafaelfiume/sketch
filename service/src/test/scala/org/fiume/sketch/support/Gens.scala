package org.fiume.sketch.support

import cats.Applicative
import org.fiume.sketch.app.Version
import org.scalacheck.{Arbitrary, Gen}

import java.time.{ZoneId, ZoneOffset, ZonedDateTime}
import java.time.temporal.ChronoUnit.MILLIS

object Gens:

  given Applicative[Gen] = new Applicative[Gen]:
    def pure[A](a: A): Gen[A] = Gen.const(a)
    def ap[A, B](ff: Gen[A => B])(fa: Gen[A]): Gen[B] = ff.flatMap(f => fa.map(f))

  def appVersions: Gen[Version] =
    val prefix = "0.1"
    val snapshot: Gen[Version] = Gen.const(Version(s"$prefix-SNAPSHOT"))
    val buildVersion: Gen[Version] = for
      currentMillis <- dateAndTime.map(_.toInstant.toEpochMilli)
      buildA <- Gen.buildableOfN[String, Char](7, Gen.alphaNumChar)
      buildNumber <- Gen.buildableOfN[String, Char](2, Gen.numChar)
    yield Version(s"$prefix.$currentMillis.$buildA.$buildNumber")
    Gen.oneOf(snapshot, buildVersion)

  def dateAndTime: Gen[ZonedDateTime] =
    for
      year <- Gen.choose(2022, 2026)
      month <- Gen.choose(1, 12)
      day <- if (month == 2) Gen.choose(1, 28) else Gen.choose(1, 30) // too simplistic: improve it
      hour <- Gen.choose(0, 23)
      minute <- Gen.choose(0, 59)
      second <- Gen.choose(0, 59)
      nanoOfSecond = 0
    yield ZonedDateTime.of(year, month, day, hour, minute, second, nanoOfSecond, ZoneOffset.UTC)
