package org.fiume.sketch.test.support

import org.fiume.sketch.shared.app.algebras.Versions.Version
import org.fiume.sketch.shared.test.Gens.DateAndTime.*
import org.scalacheck.Gen

object SketchGens:

  // Could be moved to a specific test components module?
  def versions: Gen[Version] =
    def builds = Gen.frequency(
      1 -> "snapshot",
      9 -> Gen.choose(1, 1000000001).map(_.toString)
    )
    val commits = Gen.oneOf(
      "7ab220dd4840368884322cedae340dbf253746dc",
      "20c0c8d38bdefe29f49c8c6a702066b4251c40a1",
      "ef933263dbd158bb0af2fa28f4cc62acb3824a4d",
      "10ab3ac009972f653a827658ca98d96fee32a0a4",
      "09e6e6a1eb465d4dcfdf8c591ca3b7ffe3085b3c",
      "09e6e6a1eb465d4dcfdf8c591ca3b7ffe3085b3c"
    )
    for
      build <- builds
      commit <- commits
    yield Version(build, commit)
