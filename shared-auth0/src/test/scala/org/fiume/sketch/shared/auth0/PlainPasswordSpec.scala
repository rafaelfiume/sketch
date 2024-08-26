package org.fiume.sketch.shared.auth0

import cats.syntax.all.*
import munit.ScalaCheckSuite
import org.fiume.sketch.shared.auth0.Passwords.PlainPassword
import org.fiume.sketch.shared.auth0.Passwords.PlainPassword.WeakPasswordError
import org.fiume.sketch.shared.auth0.testkit.PasswordsGens.*
import org.fiume.sketch.shared.auth0.testkit.PasswordsGens.given
import org.fiume.sketch.shared.testkit.Syntax.EitherSyntax.*
import org.scalacheck.Prop.forAll

class PlainPasswordSpec extends ScalaCheckSuite:

  test("accepts valid passwords"):
    forAll { (password: String) =>
      PlainPassword.validated(password).rightValue == PlainPassword.notValidatedFromString(password)
    }

  test("rejects short passwords"):
    forAll(shortPasswords) { shortPassword =>
      PlainPassword.validated(shortPassword).leftValue.contains(WeakPasswordError.TooShort)
    }

  test("rejects long passwords"):
    forAll(longPasswords) { longPassword =>
      PlainPassword.validated(longPassword).leftValue.contains(WeakPasswordError.TooLong)
    }

  test("rejects passwords without uppercase"):
    forAll(invalidPasswordsWithoutUppercase) { noUpperCase =>
      PlainPassword.validated(noUpperCase).leftValue.contains(WeakPasswordError.NoUpperCase)
    }

  test("rejects passwords without lowercase"):
    forAll(invalidPasswordsWithoutLowercase) { noLowerCase =>
      PlainPassword.validated(noLowerCase).leftValue.contains(WeakPasswordError.NoLowerCase)
    }

  test("rejects passwords without digit"):
    forAll(invalidPasswordsWithoutDigit) { noDigit =>
      PlainPassword.validated(noDigit).leftValue.contains(WeakPasswordError.NoDigit)
    }

  test("rejects weak passwords without special character"):
    forAll(invalidPasswordsWithoutSpecialChar) { noSpecialChar =>
      PlainPassword.validated(noSpecialChar).leftValue.contains(WeakPasswordError.NoSpecialChar)
    }

  test("rejects passwords with whitespace"):
    forAll(invalidPasswordsWithWhitespace) { withWhitespace =>
      PlainPassword.validated(withWhitespace).leftValue.contains(WeakPasswordError.Whitespace)
    }

  test("rejects passwords with invalid special chars"):
    forAll(invalidPasswordsWithInvalidSpecialChars) { withInvalidChar =>
      PlainPassword.validated(withInvalidChar).leftValue.contains(WeakPasswordError.InvalidSpecialChar)
    }

  test("rejects passwords with control chars or emojis"):
    forAll(passwordsWithControlCharsOrEmojis) { withControlCharsOrEmojis =>
      PlainPassword.validated(withControlCharsOrEmojis).leftValue.contains(WeakPasswordError.InvalidChar)
    }

  test("accumulates validation errors"):
    forAll(passwordWithMultipleInputErrors) { inputErrors =>
      val result = PlainPassword.validated(inputErrors)
      Set[PlainPassword.WeakPasswordError](
        WeakPasswordError.Whitespace,
        WeakPasswordError.InvalidSpecialChar,
        WeakPasswordError.InvalidChar
      ).subsetOf(result.leftValue.toList.toSet)
    }
