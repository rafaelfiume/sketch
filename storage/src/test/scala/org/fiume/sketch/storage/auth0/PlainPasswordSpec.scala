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
        invalidChar <- blacklistedSpecialChars
      yield password.modify(invalidChar +: _).modify(Random.shuffle(_).mkString)
    )
    forAll { (withInvalidChar: PlainPassword) =>
      PlainPassword.validated(withInvalidChar.value).leftValue.contains(PlainPassword.InvalidSpecialChar)
    }

  test("invalid passwords with control chars or emojis"):
    given Arbitrary[PlainPassword] = Arbitrary(
      for
        password <- plainPasswords
        invalidChar <- Gen.oneOf(asciiControlChars, unicodeControlChars, emojis)
      yield password.modify(invalidChar +: _).modify(Random.shuffle(_).mkString)
    )
    forAll { (withInvalidChar: PlainPassword) =>
      PlainPassword.validated(withInvalidChar.value).leftValue.contains(PlainPassword.InvalidCharater)
    }

  extension (password: PlainPassword)
    def modify(f: String => String): PlainPassword =
      PlainPassword.unsafeFromString(f(password.value))

  given Arbitrary[PlainPassword] = Arbitrary(plainPasswords)
  def plainPasswords: Gen[PlainPassword] =
    val lowercaseGen = Gen.alphaLowerChar.map(_.toString)
    val uppercaseGen = Gen.alphaUpperChar.map(_.toString)
    val digitGen = Gen.numChar.map(_.toString)
    val specialCharGen = Gen.oneOf(PlainPassword.specialChars).map(_.toString)
    for
      length <- Gen.chooseNum(PlainPassword.minLength, PlainPassword.maxLength)
      lowercase <- Gen.listOfN(length / 4, lowercaseGen)
      uppercase <- Gen.listOfN(length / 4, uppercaseGen)
      digit <- Gen.listOfN(length / 4, digitGen)
      specialChar <- Gen.listOfN(length / 4, specialCharGen)
      password = scala.util.Random.shuffle(lowercase ++ uppercase ++ digit ++ specialChar).take(length).mkString
    yield PlainPassword.unsafeFromString(password)

  def blacklistedSpecialChars: Gen[Char] = Gen.oneOf(PlainPassword.invalidSpecialChars)

  def whitespaces: Gen[Char] = Gen.oneOf(' ', '\t', '\n', '\r')

  def asciiControlChars: Gen[Char] = Gen
    .frequency(
      (10, Gen.chooseNum(0, 31)),
      (1, Gen.const(127))
    )
    .map(_.toChar)

  // see https://en.wikipedia.org/wiki/Control_character#In_Unicode
  def unicodeControlChars: Gen[Char] = Gen.choose(0x0080.toChar, 0x009f.toChar)

  def emojis: Gen[Char] = Gen.oneOf(
    0x1F600.toChar, 0x1F601.toChar, 0x1F602.toChar, 0x1F603.toChar, 0x1F604.toChar,
    0x1F605.toChar, 0x1F606.toChar, 0x1F607.toChar, 0x1F608.toChar, 0x1F609.toChar,
    0x1F60A.toChar, 0x1F60B.toChar, 0x1F60C.toChar, 0x1F60D.toChar, 0x1F60E.toChar,
    0x1F60F.toChar, 0x1F610.toChar, 0x1F611.toChar, 0x1F612.toChar, 0x1F613.toChar,
    0x1F614.toChar, 0x1F615.toChar, 0x1F616.toChar, 0x1F617.toChar, 0x1F618.toChar,
    0x1F619.toChar, 0x1F61A.toChar, 0x1F61B.toChar, 0x1F61C.toChar, 0x1F61D.toChar,
    0x1F61E.toChar, 0x1F61F.toChar, 0x1F620.toChar, 0x1F621.toChar, 0x1F622.toChar,
    0x1F623.toChar, 0x1F624.toChar, 0x1F625.toChar, 0x1F626.toChar, 0x1F627.toChar,
    0x1F628.toChar, 0x1F629.toChar, 0x1F62A.toChar, 0x1F62B.toChar, 0x1F62C.toChar,
    0x1F62D.toChar, 0x1F62E.toChar, 0x1F62F.toChar, 0x1F630.toChar, 0x1F631.toChar,
    0x1F632.toChar, 0x1F633.toChar, 0x1F634.toChar, 0x1F635.toChar, 0x1F636.toChar,
    0x1F637.toChar, 0x1F638.toChar, 0x1F639.toChar, 0x1F63A.toChar, 0x1F63B.toChar,
    0x1F63C.toChar, 0x1F63D.toChar, 0x1F63E.toChar, 0x1F63F.toChar, 0x1F640.toChar
  )