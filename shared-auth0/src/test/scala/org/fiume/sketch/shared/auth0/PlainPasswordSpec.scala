package org.fiume.sketch.shared.auth0

import cats.syntax.all.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite, ScalaCheckSuite}
import org.fiume.sketch.shared.auth0.Passwords.PlainPassword
import org.fiume.sketch.shared.auth0.Passwords.PlainPassword.WeakPasswordError
import org.fiume.sketch.shared.auth0.test.PasswordsGens.PlainPasswords.*
import org.fiume.sketch.shared.auth0.test.PasswordsGens.PlainPasswords.given
import org.fiume.sketch.shared.test.EitherSyntax.*
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.Prop.forAll

import scala.util.Random

class PlainPasswordSpec extends ScalaCheckSuite:

  test("valid passwords"):
    forAll { (password: String) =>
      PlainPassword.validated(password).rightValue == PlainPassword.notValidatedFromString(password)
    }

  test("short passwords"):
    forAll(shortPasswords) { shortPassword =>
      PlainPassword.validated(shortPassword).leftValue.contains(WeakPasswordError.TooShort)
    }

  test("long passwords"):
    forAll(longPasswords) { longPassword =>
      PlainPassword.validated(longPassword).leftValue.contains(WeakPasswordError.TooLong)
    }

  test("passwords without uppercase"):
    forAll(invalidPasswordsWithoutUppercase) { noUpperCase =>
      PlainPassword.validated(noUpperCase).leftValue.contains(WeakPasswordError.NoUpperCase)
    }

  test("passwords without lowercase"):
    forAll(invalidPasswordsWithoutLowercase) { noLowerCase =>
      PlainPassword.validated(noLowerCase).leftValue.contains(WeakPasswordError.NoLowerCase)
    }

  test("passwords without digit"):
    forAll(invalidPasswordsWithoutDigit) { noDigit =>
      PlainPassword.validated(noDigit).leftValue.contains(WeakPasswordError.NoDigit)
    }

  test("weak passwords without special character"):
    forAll(invalidPasswordsWithoutSpecialChar) { noSpecialChar =>
      PlainPassword.validated(noSpecialChar).leftValue.contains(WeakPasswordError.NoSpecialChar)
    }

  test("invalid passwords with whitespace"):
    forAll(invalidPasswordsWithWhitespace) { withWhitespace =>
      PlainPassword.validated(withWhitespace).leftValue.contains(WeakPasswordError.Whitespace)
    }

  test("invalid passwords with invalid special chars"):
    forAll(invalidPasswordsWithInvalidSpecialChars) { withInvalidChar =>
      PlainPassword.validated(withInvalidChar).leftValue.contains(WeakPasswordError.InvalidSpecialChar)
    }

  test("invalid passwords with control chars or emojis"):
    forAll(passwordsWithControlCharsOrEmojis) { withControlCharsOrEmojis =>
      PlainPassword.validated(withControlCharsOrEmojis).leftValue.contains(WeakPasswordError.InvalidChar)
    }

  test("accumulate validation errors"):
    forAll(passwordWithMultipleInputErrors) { inputErrors =>
      val result = PlainPassword.validated(inputErrors)
      Set[PlainPassword.WeakPasswordError](
        WeakPasswordError.Whitespace,
        WeakPasswordError.InvalidSpecialChar,
        WeakPasswordError.InvalidChar
      ).subsetOf(result.leftValue.toList.toSet)
    }
