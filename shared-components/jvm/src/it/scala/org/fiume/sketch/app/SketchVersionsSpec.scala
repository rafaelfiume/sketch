package org.fiume.sketch.shared.app

import cats.effect.IO
import munit.CatsEffectSuite
import org.fiume.sketch.shared.app.SketchVersions.VersionFile
import org.fiume.sketch.shared.app.Version

class SketchVersionsSpec extends CatsEffectSuite:

  test("Current service version") {
    SketchVersions.make[IO](VersionFile("service.version")).use { underTest =>
      underTest.currentVersion.map { result =>
        assertEquals(
          result,
          Version(build = "272", commit = "546fb2cfc03e4c4554c2e4de61c89080091123e8")
        )
      }
    }
  }
