package org.fiume.sketch.support

import org.fiume.sketch.app.Version
import org.fiume.sketch.test.support.Gens.DateAndTime.*
import org.scalacheck.Gen

object SketchGens:

  def appVersions: Gen[Version] =
    val prefix = "0.1"
    val snapshot: Gen[Version] = Gen.const(Version(s"$prefix-SNAPSHOT"))
    val buildVersion: Gen[Version] = for
      currentMillis <- dateAndTime.map(_.toInstant.toEpochMilli)
      buildA <- Gen.buildableOfN[String, Char](7, Gen.alphaNumChar)
      buildNumber <- Gen.buildableOfN[String, Char](2, Gen.numChar)
    yield Version(s"$prefix.$currentMillis.$buildA.$buildNumber")
    Gen.oneOf(snapshot, buildVersion)
