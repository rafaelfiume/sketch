package org.fiume.sketch.shared.auth0

import cats.syntax.all.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite, ScalaCheckSuite}
import org.fiume.sketch.shared.auth0.Passwords.PlainPassword
import org.fiume.sketch.shared.auth0.test.PasswordsGens.PlainPasswords.*
import org.fiume.sketch.shared.auth0.test.PasswordsGens.PlainPasswords.given
import org.fiume.sketch.shared.test.EitherSyntax.*
import org.fiume.sketch.shared.test.Gens.given
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.Prop.forAll

import scala.util.Random

class PlainPasswordSpec extends ScalaCheckSuite:

  test("valid passwords"):
    forAll { (password: PlainPassword) =>
      PlainPassword.validated(password.value).rightValue == password
    }

  test("short passwords"):
    forAll(shortPasswords) { shortPassword =>
      PlainPassword.validated(shortPassword.value).leftValue.contains(PlainPassword.TooShort(shortPassword.value.length))
    }

  test("long passwords"):
    forAll(longPasswords) { longPassword =>
      PlainPassword.validated(longPassword.value).leftValue.contains(PlainPassword.TooLong(longPassword.value.length))
    }

  test("passwords without uppercase"):
    forAll(invalidPasswordsWithoutUppercase) { noUpperCase =>
      PlainPassword.validated(noUpperCase.value).leftValue.contains(PlainPassword.NoUpperCase)
    }

  test("passwords without lowercase"):
    forAll(invalidPasswordsWithoutLowercase) { noLowerCase =>
      PlainPassword.validated(noLowerCase.value).leftValue.contains(PlainPassword.NoLowerCase)
    }

  test("passwords without digit"):
    forAll(invalidPasswordsWithoutDigit) { noDigit =>
      PlainPassword.validated(noDigit.value).leftValue.contains(PlainPassword.NoDigit)
    }

  test("weak passwords without special character"):
    forAll(invalidPasswordsWithoutSpecialChar) { noSpecialChar =>
      PlainPassword.validated(noSpecialChar.value).leftValue.contains(PlainPassword.NoSpecialChar)
    }

  test("invalid passwords with whitespace"):
    forAll(invalidPasswordsWithWhitespace) { withWhitespace =>
      PlainPassword.validated(withWhitespace.value).leftValue.contains(PlainPassword.Whitespace)
    }

  test("invalid passwords with invalid special chars"):
    forAll(invalidPasswordsWithInvalidSpecialChars) { withInvalidChar =>
      PlainPassword.validated(withInvalidChar.value).leftValue.contains(PlainPassword.InvalidSpecialChar)
    }

  test("invalid passwords with control chars or emojis"):
    forAll(passwordsWithControlCharsOrEmojis) { withControlCharsOrEmojis =>
      PlainPassword.validated(withControlCharsOrEmojis.value).leftValue.contains(PlainPassword.InvalidCharater)
    }

  test("accumulate validation errors"):
    given Arbitrary[PlainPassword] = Arbitrary(
      (plainPasswords, whitespaces, invalidSpecialChars, invalidChars).mapN {
        case (password, whitespace, invalidSpeciaChar, invalidChar) =>
          password.modify { value => Random.shuffle(value + whitespace :+ invalidSpeciaChar :+ invalidChar).mkString }
      }
    )
    forAll { (withInvalidChar: PlainPassword) =>
      val result = PlainPassword.validated(withInvalidChar.value)
      val expectedErrors = Set[PlainPassword.WeakPassword](
        PlainPassword.Whitespace,
        PlainPassword.InvalidSpecialChar,
        PlainPassword.InvalidCharater
      )
      expectedErrors.subsetOf(result.leftValue.toList.toSet)
    }
