package org.fiume.sketch.app

import cats.effect.IO
import munit.CatsEffectSuite
import org.fiume.sketch.algebras.Version

class SketchVersionsSpec extends CatsEffectSuite:

  test("Current service version") {
    for
      underTest <- SketchVersions.make[IO]
      result <- underTest.currentVersion
      _ <- IO { assertEquals(result, Version("0.1.1596724824884.c80e670.46")) }
    yield ()
  }
