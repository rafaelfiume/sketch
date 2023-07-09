package org.fiume.sketch.storage.auth0

import munit.{CatsEffectSuite, ScalaCheckEffectSuite, ScalaCheckSuite}
import org.fiume.sketch.shared.test.EitherSyntax.*
import org.fiume.sketch.storage.auth0.Passwords.PlainPassword
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.Prop.forAll

import scala.util.Random

class PlainPasswordSpec extends ScalaCheckSuite:

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
    given Arbitrary[PlainPassword] = Arbitrary(plainPasswords.map(_.modify(value => Random.shuffle(value + " ").mkString)))
    forAll { (withWhitespace: PlainPassword) =>
      PlainPassword.validated(withWhitespace.value).leftValue.contains(PlainPassword.Whitespace)
    }

  extension (password: PlainPassword)
    def modify(f: String => String): PlainPassword =
      PlainPassword.unsafeFromString(f(password.value))

  given Arbitrary[PlainPassword] = Arbitrary(plainPasswords)
  def plainPasswords: Gen[PlainPassword] =
    val lowercaseGen = Gen.alphaLowerChar.map(_.toString)
    val uppercaseGen = Gen.alphaUpperChar.map(_.toString)
    val digitGen = Gen.numChar.map(_.toString)
    val specialCharGen = Gen
      // TODO remove blacklisted special characters
      .oneOf('!', '@', '#', '$', '%', '^', '&', '*', '(', ')', '-', '_', '+', '=', '[', ']', '{', '}', '|', '\\', ';', ':', '\'',
             '"', ',', '.', '<', '>', '/', '?')
      .map(_.toString)
    for
      length <- Gen.chooseNum(PlainPassword.minLength, PlainPassword.maxLength)
      lowercase <- Gen.listOfN(length / 4, lowercaseGen)
      uppercase <- Gen.listOfN(length / 4, uppercaseGen)
      digit <- Gen.listOfN(length / 4, digitGen)
      specialChar <- Gen.listOfN(length / 4, specialCharGen)
      password = scala.util.Random.shuffle(lowercase ++ uppercase ++ digit ++ specialChar).take(length).mkString
    yield PlainPassword.unsafeFromString(password)

    // define a generator that returns whitespace characters
  def whitespaces: Gen[Char] = Gen.oneOf(' ', '\t', '\n', '\r')

  def asciiControlChars: Gen[Char] = Gen
    .frequency(
      (10, Gen.chooseNum(0, 31)),
      (1, Gen.const(127))
    )
    .map(_.toChar)

  /*
   *  Special characters with specific meanings:
   *
   *  Null terminator (\u0000): Used to terminate strings in some programming languages.
   *  Single quote ('): Can cause issues when used in certain database queries or input validation.
   *  Double quote ("): Can cause issues when used in certain database queries or input validation.
   *  Backtick (`): Can be used in command injection attacks or certain programming contexts.
   */
  // TODO Blacklist some more?
  def invalidSpecialChars: Gen[Char] = Gen.oneOf('\u0000', '\'', '"', '`')
