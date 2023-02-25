package org.fiume.sketch.app

import cats.effect.IO
import org.fiume.sketch.algebras.Version
import munit.CatsEffectSuite

class SketchVersionsSpec extends CatsEffectSuite:

  test("Version should return current app version") {
    for
      underTest <- SketchVersions.make[IO]
      appVersion <- underTest.currentVersion
      _ <- IO { assertEquals(appVersion, Version("0.1.1596724824884.c80e670.46")) }
    yield ()
  }
