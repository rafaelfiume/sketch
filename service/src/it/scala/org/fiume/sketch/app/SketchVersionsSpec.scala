package org.fiume.sketch.app

import cats.effect.IO
import munit.CatsEffectSuite
import org.fiume.sketch.app.SketchVersions.VersionFile
import org.fiume.sketch.shared.app.Version
import org.fiume.sketch.shared.app.Version.{Build, Commit, Environment}

class SketchVersionsSpec extends CatsEffectSuite:

  test("Current service version"):
    val env = Environment("Test")
    SketchVersions.make[IO](env, VersionFile("service.version")).use { underTest =>
      underTest.currentVersion.map { result =>
        assertEquals(
          result,
          Version(env, build = Build("272"), commit = Commit("546fb2cfc03e4c4554c2e4de61c89080091123e8"))
        )
      }
    }
