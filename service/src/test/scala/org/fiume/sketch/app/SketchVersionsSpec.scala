package org.fiume.sketch.app

import cats.effect.IO
import munit.CatsEffectSuite
import org.fiume.sketch.app.SketchVersions.VersionFile
import org.fiume.sketch.shared.app.Version

class SketchVersionsSpec extends CatsEffectSuite:

  test("Current service version") {
    SketchVersions.make[IO](VersionFile("sketch.version")).use { versions =>
      for
        result <- versions.currentVersion
        _ <- IO {
          assertEquals(
            result,
            Version(build = "272", commit = "546fb2cfc03e4c4554c2e4de61c89080091123e8")
          )
        }
      yield ()
    }
  }
