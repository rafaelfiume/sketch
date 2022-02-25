package org.fiume.sketch.support

import org.scalacheck.{Arbitrary, Gen}
import org.fiume.sketch.app.Version

import java.time.temporal.ChronoUnit.MILLIS
import java.time.{ZoneId, ZoneOffset, ZonedDateTime}

object Gens:

  def appVersions: Gen[Version] =
    val prefix = "0.1"
    val snapshot: Gen[Version] = Gen.const(Version(s"$prefix-SNAPSHOT"))
    val buildVersion: Gen[Version] = for
      currentMillis <- Customs.dateAndTime.map(_.toInstant.toEpochMilli)
      buildA <- Gen.buildableOfN[String, Char](7, Gen.alphaNumChar)
      buildNumber <- Gen.buildableOfN[String, Char](2, Gen.numChar)
    yield Version(s"$prefix.$currentMillis.$buildA.$buildNumber")
    Gen.oneOf(snapshot, buildVersion)

  object Customs:
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
