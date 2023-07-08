package org.fiume.sketch.storage.auth0

import munit.ScalaCheckSuite
import org.fiume.sketch.shared.test.Gens
import org.fiume.sketch.storage.auth0.{HashedPassword, Salt}
import org.scalacheck.{Arbitrary, Gen, Prop, ShrinkLowPriority}
import org.scalacheck.Prop.{forAll, propBoolean}

// TODO Extract it
case class PlainPassword(value: String) extends AnyVal

class HashedPasswordSpec extends ScalaCheckSuite with ShrinkLowPriority:

  // tests are very expensive to run
  override def scalaCheckTestParameters = super.scalaCheckTestParameters
    .withMinSuccessfulTests(2)

  // TODO Duplicated from PostgresStoreSpec (with variation)
  def salts: Gen[Salt] = {
    import cats.effect.IO
    import cats.effect.unsafe.IORuntime
    given IORuntime = IORuntime.global
    Gen.delay(Salt.generate[IO]().unsafeRunSync())
  }
  given Arbitrary[Salt] = Arbitrary(salts)

  def passwords: Gen[PlainPassword] = Gens.Strings.alphaNumString(8, 50).map(PlainPassword.apply)
  given Arbitrary[PlainPassword] = Arbitrary(passwords)

  test("hashing passwords is consistent for the same input (referencially transparent)") {
    forAll { (password: PlainPassword, salt: Salt) =>
      val hashedPassword1 = HashedPassword.hashPassword(password.value, salt)
      val hashedPassword2 = HashedPassword.hashPassword(password.value, salt)
      hashedPassword1.value == hashedPassword2.value
    }
  }

  test("hashing the same password with different salts produces different hashes") {
    forAll { (password: PlainPassword, salt: Salt, differentSalt: Salt) =>
      (salt != differentSalt) ==> {
        val hashedPassword1 = HashedPassword.hashPassword(password.value, salt)
        val hashedPassword2 = HashedPassword.hashPassword(password.value, differentSalt)
        hashedPassword1.value != hashedPassword2.value
      }
    }
  }

  test("hashing different passwords with the same salt produces different hashes") {
    forAll { (password: PlainPassword, differentPassword: PlainPassword, salt: Salt) =>
      (password != differentPassword) ==> {
        val hashedPassword1 = HashedPassword.hashPassword(password.value, salt)
        val hashedPassword2 = HashedPassword.hashPassword(differentPassword.value, salt)
        hashedPassword1.value != hashedPassword2.value
      }
    }
  }

  test("verifying a plain password against its hashed counterpart returns true") {
    forAll { (password: PlainPassword, salt: Salt) =>
      val hashedPassword = HashedPassword.hashPassword(password.value, salt)
      HashedPassword.verifyPassword(password.value, hashedPassword)
    }
  }

  test("verifying a plain password against a different hashed password returns false") {
    forAll { (password: PlainPassword, differentPassword: PlainPassword, salt: Salt) =>
      (password != differentPassword) ==> {
        val hashedPassword = HashedPassword.hashPassword(password.value, salt)
        !HashedPassword.verifyPassword(differentPassword.value, hashedPassword)
      }
    }
  }

  test("just to see it working".ignore) {
    forAll { (password: PlainPassword, salt: Salt) =>
      val hashedPassword1 = HashedPassword.hashPassword(password.value, salt)
      println("password: " + password.value)
      println("hashedPassword1: " + hashedPassword1.value)
      println("salt: " + salt.base64Value)
      true
    }
  }
