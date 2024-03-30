package org.fiume.sketch.shared.auth0

import cats.effect.IO
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.fiume.sketch.shared.auth0.Passwords.Salt
import org.scalacheck.{Gen, ShrinkLowPriority}
import org.scalacheck.effect.PropF.forAllF

class SaltSpec extends CatsEffectSuite with ScalaCheckEffectSuite with ShrinkLowPriority:

  test("generates a different salt each time"):
    forAllF(Gen.choose(1, 100)) { (_: Int) =>
      for
        salt1 <- Salt.generate[IO]()
        salt2 <- Salt.generate[IO]()
      yield assertNotEquals(salt1, salt2)
    }

  test("generates a salt from a string"):
    forAllF(Gen.choose(1, 100)) { (_: Int) =>
      for salt <- Salt.generate[IO]()
      yield assertEquals(Salt.notValidatedFromString(salt.base64Value), salt)
    }

  test("salt length is 29 characters"):
    forAllF(Gen.choose(1, 100)) { (_: Int) =>
      for salt <- Salt.generate[IO]()
      yield assertEquals(salt.base64Value.length, 29)
    }

  test("salt is url safe"):
    forAllF(Gen.choose(1, 100)) { (_: Int) =>
      for
        salt <- Salt.generate[IO]()
        _ <- IO.delay { new java.net.URL(s"https://artigiani.it/${salt.base64Value}") }
      yield ()
    }
