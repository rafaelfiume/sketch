package org.fiume.sketch.shared.auth0

import munit.ScalaCheckSuite
import org.fiume.sketch.shared.auth0.Passwords.{HashedPassword, PlainPassword, Salt}
import org.fiume.sketch.shared.auth0.testkit.PasswordsGens.given
import org.scalacheck.{Prop, ShrinkLowPriority}
import org.scalacheck.Prop.{forAll, propBoolean}

class HashedPasswordSpec extends ScalaCheckSuite with ShrinkLowPriority:

  // tests are very expensive to run
  override def scalaCheckTestParameters = super.scalaCheckTestParameters.withMinSuccessfulTests(1)

  test("hashing passwords is consistent for the same input (referencially transparent)"):
    forAll { (password: PlainPassword, salt: Salt) =>
      val hashedPassword1 = HashedPassword.hashPassword(password, salt)
      val hashedPassword2 = HashedPassword.hashPassword(password, salt)
      hashedPassword1.base64Value == hashedPassword2.base64Value
    }

  test("same password with different salts produces different hashes"):
    forAll { (password: PlainPassword, salt: Salt, differentSalt: Salt) =>
      (salt != differentSalt) ==> {
        val hashedPassword1 = HashedPassword.hashPassword(password, salt)
        val hashedPassword2 = HashedPassword.hashPassword(password, differentSalt)
        hashedPassword1.base64Value != hashedPassword2.base64Value
      }
    }

  test("different passwords with same salt produces different hashes"):
    forAll { (password: PlainPassword, differentPassword: PlainPassword, salt: Salt) =>
      (password != differentPassword) ==> {
        val hashedPassword1 = HashedPassword.hashPassword(password, salt)
        val hashedPassword2 = HashedPassword.hashPassword(differentPassword, salt)
        hashedPassword1.base64Value != hashedPassword2.base64Value
      }
    }

  test("hashed password length is 60 characters"):
    forAll { (password: PlainPassword, salt: Salt) =>
      val hashedPassword = HashedPassword.hashPassword(password, salt)
      hashedPassword.base64Value.length == 60
    }

  test("plain password matches hashed version"):
    forAll { (password: PlainPassword, salt: Salt) =>
      val hashedPassword = HashedPassword.hashPassword(password, salt)
      HashedPassword.verifyPassword(password, hashedPassword)
    }

  test("plain password does not match hashed version"):
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
