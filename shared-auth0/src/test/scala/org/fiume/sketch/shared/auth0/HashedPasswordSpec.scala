package org.fiume.sketch.shared.auth0

import munit.ScalaCheckSuite
import org.fiume.sketch.shared.auth0.Passwords.{HashedPassword, PlainPassword, Salt}
import org.fiume.sketch.shared.auth0.test.PasswordGens
import org.fiume.sketch.shared.test.Gens
import org.scalacheck.{Arbitrary, Gen, Prop, ShrinkLowPriority}
import org.scalacheck.Prop.{forAll, propBoolean}

class HashedPasswordSpec extends ScalaCheckSuite with PasswordGens with ShrinkLowPriority:

  // tests are very expensive to run
  override def scalaCheckTestParameters = super.scalaCheckTestParameters
    .withMinSuccessfulTests(2)

  test("hashing passwords is consistent for the same input (referencially transparent)"):
    forAll { (password: PlainPassword, salt: Salt) =>
      val hashedPassword1 = HashedPassword.hashPassword(password, salt)
      val hashedPassword2 = HashedPassword.hashPassword(password, salt)
      hashedPassword1.base64Value == hashedPassword2.base64Value
    }

  test("hashing the same password with different salts produces different hashes"):
    forAll { (password: PlainPassword, salt: Salt, differentSalt: Salt) =>
      (salt != differentSalt) ==> {
        val hashedPassword1 = HashedPassword.hashPassword(password, salt)
        val hashedPassword2 = HashedPassword.hashPassword(password, differentSalt)
        hashedPassword1.base64Value != hashedPassword2.base64Value
      }
    }

  test("hashing different passwords with the same salt produces different hashes"):
    forAll { (password: PlainPassword, differentPassword: PlainPassword, salt: Salt) =>
      (password != differentPassword) ==> {
        val hashedPassword1 = HashedPassword.hashPassword(password, salt)
        val hashedPassword2 = HashedPassword.hashPassword(differentPassword, salt)
        hashedPassword1.base64Value != hashedPassword2.base64Value
      }
    }

  test("hashed password is 60 characters long"):
    forAll { (password: PlainPassword, salt: Salt) =>
      val hashedPassword = HashedPassword.hashPassword(password, salt)
      hashedPassword.base64Value.length == 60
    }

  test("verifying a plain password against its hashed counterpart returns true"):
    forAll { (password: PlainPassword, salt: Salt) =>
      val hashedPassword = HashedPassword.hashPassword(password, salt)
      HashedPassword.verifyPassword(password, hashedPassword)
    }

  test("verifying a plain password against a different hashed password returns false"):
    forAll { (password: PlainPassword, differentPassword: PlainPassword, salt: Salt) =>
      (password != differentPassword) ==> {
        val hashedPassword = HashedPassword.hashPassword(password, salt)
        !HashedPassword.verifyPassword(differentPassword, hashedPassword)
      }
    }

  test("just to see it working".ignore):
    forAll { (password: PlainPassword, salt: Salt) =>
      val hashedPassword1 = HashedPassword.hashPassword(password, salt)
      println("password: " + password.value)
      println("hashedPassword1: " + hashedPassword1.base64Value)
      println("salt: " + salt.base64Value)
      true
    }

  // TODO Improve this to conform to the password policy
  def passwords: Gen[PlainPassword] = Gens.Strings.alphaNumString(8, 64).map(PlainPassword.unsafeFromString)
  given Arbitrary[PlainPassword] = Arbitrary(passwords)
