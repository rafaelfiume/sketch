package org.fiume.sketch.shared.auth

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import munit.ScalaCheckSuite
import org.fiume.sketch.shared.auth.domain.Passwords.{HashedPassword, PlainPassword, Salt}
import org.fiume.sketch.shared.auth.testkit.PasswordsGens.given
import org.scalacheck.{Prop, ShrinkLowPriority}
import org.scalacheck.Prop.{forAll, propBoolean}

class HashedPasswordSpec extends ScalaCheckSuite with ShrinkLowPriority:

  // tests are very expensive to run
  override def scalaCheckTestParameters = super.scalaCheckTestParameters.withMinSuccessfulTests(1)

  given IORuntime = IORuntime.global

  test("hashing passwords is consistent for the same input (referencially transparent)"):
    forAll { (password: PlainPassword, salt: Salt) =>
      val hashedPassword1 = HashedPassword.hashPassword[IO](password, salt).unsafeRunSync()
      val hashedPassword2 = HashedPassword.hashPassword[IO](password, salt).unsafeRunSync()
      hashedPassword1.base64Value == hashedPassword2.base64Value
    }

  test("same password with different salts produces different hashes"):
    forAll { (password: PlainPassword, salt: Salt, differentSalt: Salt) =>
      (salt != differentSalt) ==> {
        val hashedPassword1 = HashedPassword.hashPassword[IO](password, salt).unsafeRunSync()
        val hashedPassword2 = HashedPassword.hashPassword[IO](password, differentSalt).unsafeRunSync()
        hashedPassword1.base64Value != hashedPassword2.base64Value
      }
    }

  test("different passwords with same salt produces different hashes"):
    forAll { (password: PlainPassword, differentPassword: PlainPassword, salt: Salt) =>
      (password != differentPassword) ==> {
        val hashedPassword1 = HashedPassword.hashPassword[IO](password, salt).unsafeRunSync()
        val hashedPassword2 = HashedPassword.hashPassword[IO](differentPassword, salt).unsafeRunSync()
        hashedPassword1.base64Value != hashedPassword2.base64Value
      }
    }

  test("hashed password length is 60 characters"):
    forAll { (password: PlainPassword, salt: Salt) =>
      val hashedPassword = HashedPassword.hashPassword[IO](password, salt).unsafeRunSync()
      hashedPassword.base64Value.length == 60
    }

  test("plain password matches hashed version"):
    forAll { (password: PlainPassword, salt: Salt) =>
      val hashedPassword = HashedPassword.hashPassword[IO](password, salt).unsafeRunSync()
      HashedPassword.verifyPassword[IO](password, hashedPassword).unsafeRunSync()
    }

  test("plain password does not match hashed version"):
    forAll { (password: PlainPassword, differentPassword: PlainPassword, salt: Salt) =>
      (password != differentPassword) ==> {
        val hashedPassword = HashedPassword.hashPassword[IO](password, salt).unsafeRunSync()
        !HashedPassword.verifyPassword[IO](differentPassword, hashedPassword).unsafeRunSync()
      }
    }

  test("just to see it working".ignore):
    forAll { (password: PlainPassword, salt: Salt) =>
      val hashedPassword1 = HashedPassword.hashPassword[IO](password, salt).unsafeRunSync()
      println("password: " + password.value)
      println("hashedPassword1: " + hashedPassword1.base64Value)
      println("salt: " + salt.base64Value)
      true
    }
