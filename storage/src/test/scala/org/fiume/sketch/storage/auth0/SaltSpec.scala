package org.fiume.sketch.storage.auth0

import cats.effect.IO
import cats.implicits.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.scalacheck.{Gen, Shrink}
import org.scalacheck.effect.PropF.forAllF

class SaltSpec extends CatsEffectSuite with ScalaCheckEffectSuite:
  // shrinking just make failing tests messages more obscure
  given noShrink[T]: Shrink[T] = Shrink.shrinkAny

  test("generate a different salt each time") {
    forAllF(Gen.choose(1, 100)) { (_: Int) =>
      for
        salt1 <- Salt.generate[IO]()
        salt2 <- Salt.generate[IO]()
      yield assertNotEquals(salt1, salt2)
    }
  }

  test("generate a 32 bytes salt") {
    forAllF(Gen.choose(1, 100)) { (_: Int) =>
      for salt <- Salt.generate[IO]()
      yield assertEquals(salt.bytes.length, 32)
    }
  }

  test("generate a base64 salt that is 44 character length") {
    forAllF(Gen.choose(1, 100)) { (_: Int) =>
      for salt <- Salt.generate[IO]()
      yield assertEquals(salt.base64Value.length, 44)
    }
  }

  test("generate a salt from a string") {
    forAllF(Gen.choose(1, 100)) { (_: Int) =>
      for salt <- Salt.generate[IO]()
      yield assertEquals(Salt.unsafeFromString(salt.base64Value), salt)
    }
  }
