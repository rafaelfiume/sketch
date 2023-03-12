package org.fiume.sketch.app

import cats.effect.IO
import munit.CatsEffectSuite
import org.fiume.sketch.shared.app.Version

class SketchVersionsSpec extends CatsEffectSuite:

  test("Current service version") {
    SketchVersions.make[IO].use { versions =>
      for
        result <- versions.currentVersion
        _ <- IO { assertEquals(result, Version("0.1.1596724824884.c80e670.46")) }
      yield ()
    }
  }
