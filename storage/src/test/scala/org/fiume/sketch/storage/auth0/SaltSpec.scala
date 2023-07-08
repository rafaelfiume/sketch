package org.fiume.sketch.storage.auth0

import cats.effect.IO
import cats.implicits.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.fiume.sketch.storage.auth0.Passwords.Salt
import org.scalacheck.{Gen, Shrink, ShrinkLowPriority}
import org.scalacheck.effect.PropF.forAllF

class SaltSpec extends CatsEffectSuite with ScalaCheckEffectSuite with ShrinkLowPriority:

  test("generate a different salt each time") {
    forAllF(Gen.choose(1, 100)) { (_: Int) =>
      for
        salt1 <- Salt.generate[IO]()
        salt2 <- Salt.generate[IO]()
      yield assertNotEquals(salt1, salt2)
    }
  }

  test("generate a base64 salt that is 29 character length") {
    forAllF(Gen.choose(1, 100)) { (_: Int) =>
      for salt <- Salt.generate[IO]()
      yield assertEquals(salt.base64Value.length, 29)
    }
  }

  test("generate a salt from a string") {
    forAllF(Gen.choose(1, 100)) { (_: Int) =>
      for salt <- Salt.generate[IO]()
      yield assertEquals(Salt.unsafeFromString(salt.base64Value), salt)
    }
  }

  test("generate a salt that is url safe") {
    forAllF(Gen.choose(1, 100)) { (_: Int) =>
      for
        salt <- Salt.generate[IO]()
        _ <- IO.delay { new java.net.URL(s"https://artigiani.it/${salt.base64Value}") }
      yield ()
    }
  }
