package org.fiume.sketch.shared.auth0

import cats.syntax.all.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite, ScalaCheckSuite}
import org.fiume.sketch.shared.auth0.Passwords.PlainPassword
import org.fiume.sketch.shared.auth0.Passwords.PlainPassword.WeakPassword
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
      PlainPassword.validated(shortPassword).leftValue.contains(WeakPassword.TooShort)
    }

  test("long passwords"):
    forAll(longPasswords) { longPassword =>
      PlainPassword.validated(longPassword).leftValue.contains(WeakPassword.TooLong)
    }

  test("passwords without uppercase"):
    forAll(invalidPasswordsWithoutUppercase) { noUpperCase =>
      PlainPassword.validated(noUpperCase).leftValue.contains(WeakPassword.NoUpperCase)
    }

  test("passwords without lowercase"):
    forAll(invalidPasswordsWithoutLowercase) { noLowerCase =>
      PlainPassword.validated(noLowerCase).leftValue.contains(WeakPassword.NoLowerCase)
    }

  test("passwords without digit"):
    forAll(invalidPasswordsWithoutDigit) { noDigit =>
      PlainPassword.validated(noDigit).leftValue.contains(WeakPassword.NoDigit)
    }

  test("weak passwords without special character"):
    forAll(invalidPasswordsWithoutSpecialChar) { noSpecialChar =>
      PlainPassword.validated(noSpecialChar).leftValue.contains(WeakPassword.NoSpecialChar)
    }

  test("invalid passwords with whitespace"):
    forAll(invalidPasswordsWithWhitespace) { withWhitespace =>
      PlainPassword.validated(withWhitespace).leftValue.contains(WeakPassword.Whitespace)
    }

  test("invalid passwords with invalid special chars"):
    forAll(invalidPasswordsWithInvalidSpecialChars) { withInvalidChar =>
      PlainPassword.validated(withInvalidChar).leftValue.contains(WeakPassword.InvalidSpecialChar)
    }

  test("invalid passwords with control chars or emojis"):
    forAll(passwordsWithControlCharsOrEmojis) { withControlCharsOrEmojis =>
      PlainPassword.validated(withControlCharsOrEmojis).leftValue.contains(WeakPassword.InvalidChar)
    }

  test("accumulate validation errors"):
    forAll(passwordWithMultipleInputErrors) { inputErrors =>
      val result = PlainPassword.validated(inputErrors)
      Set[PlainPassword.WeakPassword](
        WeakPassword.Whitespace,
        WeakPassword.InvalidSpecialChar,
        WeakPassword.InvalidChar
      ).subsetOf(result.leftValue.toList.toSet)
    }
