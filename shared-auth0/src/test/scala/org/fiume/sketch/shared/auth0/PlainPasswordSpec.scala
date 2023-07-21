package org.fiume.sketch.shared.auth0

import munit.{CatsEffectSuite, ScalaCheckEffectSuite, ScalaCheckSuite}
import org.fiume.sketch.shared.auth0.Passwords
import org.fiume.sketch.shared.auth0.Passwords.PlainPassword
import org.fiume.sketch.shared.auth0.test.PasswordsGens
import org.fiume.sketch.shared.test.EitherSyntax.*
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.Prop.forAll

import scala.util.Random

class PlainPasswordSpec extends ScalaCheckSuite with PasswordsGens:

  test("valid passwords"):
    forAll { (password: PlainPassword) =>
      PlainPassword.validated(password.value).rightValue == password
    }

  test("weak short passwords"):
    given Arbitrary[PlainPassword] = Arbitrary(
      for
        password <- plainPasswords
        shortSize <- Gen.choose(0, PlainPassword.minLength - 1)
      yield password.modify(_.take(shortSize))
    )
    forAll { (shortPassword: PlainPassword) =>
      PlainPassword.validated(shortPassword.value).leftValue.contains(PlainPassword.TooShort(shortPassword.value.length))
    }

  test("weak long passwords"):
    given Arbitrary[PlainPassword] = Arbitrary(
      for
        password <- plainPasswords
        extraSize <- Gen.choose(PlainPassword.maxLength, 100)
        extraChars <- Gen.listOfN(extraSize, Gen.alphaNumChar).map(_.mkString)
      yield password.modify(_ + extraChars)
    )
    forAll { (longPassword: PlainPassword) =>
      PlainPassword.validated(longPassword.value).leftValue.contains(PlainPassword.TooLong(longPassword.value.length))
    }

  test("weak passwords without uppercase"):
    given Arbitrary[PlainPassword] = Arbitrary(plainPasswords.map(_.modify(_.toLowerCase())))
    forAll { (noUpperCase: PlainPassword) =>
      PlainPassword.validated(noUpperCase.value).leftValue.contains(PlainPassword.NoUpperCase)
    }

  test("weak passwords without lowercase"):
    given Arbitrary[PlainPassword] = Arbitrary(plainPasswords.map(_.modify(_.toUpperCase)))
    forAll { (noLowerCase: PlainPassword) =>
      PlainPassword.validated(noLowerCase.value).leftValue.contains(PlainPassword.NoLowerCase)
    }

  test("weak passwords without digit"):
    given Arbitrary[PlainPassword] = Arbitrary(plainPasswords.map(_.modify(_.filterNot(_.isDigit))))
    forAll { (noDigit: PlainPassword) =>
      PlainPassword.validated(noDigit.value).leftValue.contains(PlainPassword.NoDigit)
    }

  test("weak passwords without special character"):
    given Arbitrary[PlainPassword] = Arbitrary(plainPasswords.map(_.modify(_.filter(_.isLetterOrDigit))))
    forAll { (noSpecialChar: PlainPassword) =>
      PlainPassword.validated(noSpecialChar.value).leftValue.contains(PlainPassword.NoSpecialChar)
    }

  test("invalid passwords with whitespace"):
    given Arbitrary[PlainPassword] = Arbitrary(
      for
        password <- plainPasswords
        whitespace <- whitespaces
      yield password.modify(whitespace +: _).modify(Random.shuffle(_).mkString)
    )
    forAll { (withWhitespace: PlainPassword) =>
      PlainPassword.validated(withWhitespace.value).leftValue.contains(PlainPassword.Whitespace)
    }

  test("invalid passwords with invalid special chars"):
    given Arbitrary[PlainPassword] = Arbitrary(
      for
        password <- plainPasswords
        invalidChar <- invalidSpecialChars
      yield password.modify(invalidChar +: _).modify(Random.shuffle(_).mkString)
    )
    forAll { (withInvalidChar: PlainPassword) =>
      PlainPassword.validated(withInvalidChar.value).leftValue.contains(PlainPassword.InvalidSpecialChar)
    }

  test("invalid passwords with control chars or emojis"):
    given Arbitrary[PlainPassword] = Arbitrary(
      for
        password <- plainPasswords
        invalidChar <- invalidChars
      yield password.modify(invalidChar +: _).modify(Random.shuffle(_).mkString)
    )
    forAll { (withInvalidChar: PlainPassword) =>
      PlainPassword.validated(withInvalidChar.value).leftValue.contains(PlainPassword.InvalidCharater)
    }

  test("accumulate validation errors"):
    import cats.syntax.all.*
    import org.fiume.sketch.shared.test.Gens.given
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

  extension (password: PlainPassword)
    def modify(f: String => String): PlainPassword =
      PlainPassword.notValidatedFromString(f(password.value))

  def invalidSpecialChars: Gen[Char] = Gen.oneOf(PlainPassword.invalidSpecialChars)

  def whitespaces: Gen[Char] = Gen.oneOf(' ', '\t', '\n', '\r')

  def invalidChars: Gen[Char] = Gen.oneOf(asciiControlChars, unicodeControlChars, emojis)

  def asciiControlChars: Gen[Char] = Gen
    .frequency(
      (10, Gen.chooseNum(0, 31)),
      (1, Gen.const(127))
    )
    .map(_.toChar)

  // see https://en.wikipedia.org/wiki/Control_character#In_Unicode
  def unicodeControlChars: Gen[Char] = Gen.choose(0x0080.toChar, 0x009f.toChar)

  def emojis: Gen[Char] = Gen.oneOf(
    0x1f600.toChar,
    0x1f601.toChar,
    0x1f602.toChar,
    0x1f603.toChar,
    0x1f604.toChar,
    0x1f605.toChar,
    0x1f606.toChar,
    0x1f607.toChar,
    0x1f608.toChar,
    0x1f609.toChar,
    0x1f60a.toChar,
    0x1f60b.toChar,
    0x1f60c.toChar,
    0x1f60d.toChar,
    0x1f60e.toChar,
    0x1f60f.toChar,
    0x1f610.toChar,
    0x1f611.toChar,
    0x1f612.toChar,
    0x1f613.toChar,
    0x1f614.toChar,
    0x1f615.toChar,
    0x1f616.toChar,
    0x1f617.toChar,
    0x1f618.toChar,
    0x1f619.toChar,
    0x1f61a.toChar,
    0x1f61b.toChar,
    0x1f61c.toChar,
    0x1f61d.toChar,
    0x1f61e.toChar,
    0x1f61f.toChar,
    0x1f620.toChar,
    0x1f621.toChar,
    0x1f622.toChar,
    0x1f623.toChar,
    0x1f624.toChar,
    0x1f625.toChar,
    0x1f626.toChar,
    0x1f627.toChar,
    0x1f628.toChar,
    0x1f629.toChar,
    0x1f62a.toChar,
    0x1f62b.toChar,
    0x1f62c.toChar,
    0x1f62d.toChar,
    0x1f62e.toChar,
    0x1f62f.toChar,
    0x1f630.toChar,
    0x1f631.toChar,
    0x1f632.toChar,
    0x1f633.toChar,
    0x1f634.toChar,
    0x1f635.toChar,
    0x1f636.toChar,
    0x1f637.toChar,
    0x1f638.toChar,
    0x1f639.toChar,
    0x1f63a.toChar,
    0x1f63b.toChar,
    0x1f63c.toChar,
    0x1f63d.toChar,
    0x1f63e.toChar,
    0x1f63f.toChar,
    0x1f640.toChar
  )
