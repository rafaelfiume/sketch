package org.fiume.sketch.shared.auth0.testkit

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.fiume.sketch.shared.auth0.Passwords.{HashedPassword, PlainPassword, Salt}
import org.fiume.sketch.shared.auth0.testkit.PasswordsGens.*
import org.scalacheck.{Arbitrary, Gen}

import scala.util.Random

object PasswordsGens:

  given Arbitrary[PlainPassword] = Arbitrary(validPlainPasswords)
  def validPlainPasswords: Gen[PlainPassword] = plainPasswords.map(PlainPassword.notValidatedFromString)

  given Arbitrary[String] = Arbitrary(plainPasswords)
  def plainPasswords: Gen[String] =
    val lowercaseGen = Gen.alphaLowerChar.map(_.toString)
    val uppercaseGen = Gen.alphaUpperChar.map(_.toString)
    val digitGen = Gen.numChar.map(_.toString)
    val specialCharGen = Gen.oneOf(PlainPassword.specialChars).map(_.toString)
    (for
      length <- Gen.chooseNum(PlainPassword.minLength, PlainPassword.maxLength)
      lowercase <- Gen.listOfN(length / 4, lowercaseGen)
      uppercase <- Gen.listOfN(length / 4, uppercaseGen)
      digit <- Gen.listOfN(length / 4, digitGen)
      specialChar <- Gen.listOfN(length / 4, specialCharGen)
      password = scala.util.Random.shuffle(lowercase ++ uppercase ++ digit ++ specialChar).take(length).mkString
    yield password) :| "valid passwords"

  def shortPasswords: Gen[String] =
    (for
      password <- plainPasswords
      shortSize <- Gen.choose(0, PlainPassword.minLength - 1)
    yield password.take(shortSize)) :| "short passwords"

  def longPasswords: Gen[String] =
    (for
      password <- plainPasswords
      extraSize <- Gen.choose(PlainPassword.maxLength, 100)
      extraChars <- Gen.listOfN(extraSize, Gen.alphaNumChar).map(_.mkString)
    yield password + extraChars) :| "long passwords"

  def invalidPasswordsWithoutUppercase: Gen[String] =
    plainPasswords.map(_.toLowerCase()) :| "passwords without uppercase"

  def invalidPasswordsWithoutLowercase: Gen[String] =
    plainPasswords.map(_.toUpperCase) :| "passwords without lowercase"

  def invalidPasswordsWithoutDigit: Gen[String] =
    plainPasswords.map(_.filterNot(_.isDigit)) :| "passwords without digit"

  def invalidPasswordsWithoutSpecialChar: Gen[String] =
    plainPasswords.map(_.filter(_.isLetterOrDigit)) :| "passwords without special character"

  def invalidPasswordsWithWhitespace: Gen[String] =
    (for
      password <- plainPasswords
      whitespace <- whitespaces
    yield Random.shuffle(whitespace +: password).mkString) :| "passwords with whitespace"

  def invalidPasswordsWithInvalidSpecialChars: Gen[String] =
    (for
      password <- plainPasswords
      invalidChar <- invalidSpecialChars
    yield Random.shuffle(invalidChar +: password).mkString) :| "passwords with invalid special character"

  def passwordsWithControlCharsOrEmojis: Gen[String] =
    (for
      password <- plainPasswords
      invalidChar <- invalidChars
    yield Random.shuffle(invalidChar +: password).mkString) :| "passwords with control chars or emojis"

  def passwordWithMultipleInputErrors: Gen[String] =
    (for
      plainPassword <- plainPasswords
      whitespace <- whitespaces
      invalidSpecialChar <- invalidSpecialChars
      invalidChar <- invalidChars
    yield Random
      .shuffle(whitespace +: invalidSpecialChar +: invalidChar +: plainPassword)
      .mkString) :| "passwords with multiple input errors"

  def oneOfPasswordInputErrors: Gen[String] = Gen.oneOf(
    shortPasswords,
    longPasswords,
    invalidPasswordsWithoutUppercase,
    invalidPasswordsWithoutLowercase,
    invalidPasswordsWithoutDigit,
    invalidPasswordsWithoutSpecialChar,
    invalidPasswordsWithWhitespace,
    invalidPasswordsWithInvalidSpecialChars,
    passwordsWithControlCharsOrEmojis
  ) :| "one of password input errors"

  given Arbitrary[Salt] = Arbitrary(salts)
  def salts: Gen[Salt] =
    given IORuntime = IORuntime.global
    Gen.delay(Salt.generate[IO]().unsafeRunSync())

  // a bcrypt hash approximation for efficience (store assumes correctness)
  given Arbitrary[HashedPassword] = Arbitrary(fakeHashedPasswords)
  def fakeHashedPasswords: Gen[HashedPassword] =
    Gen.listOfN(60, bcryptBase64Char).map(_.mkString).map(HashedPassword.notValidatedFromString)

  private def whitespaces: Gen[Char] = Gen.oneOf(' ', '\t', '\n', '\r')

  private def invalidSpecialChars: Gen[Char] = Gen.oneOf(PlainPassword.invalidSpecialChars)

  private def invalidChars: Gen[Char] = Gen.oneOf(asciiControlChars, unicodeControlChars, emojis)

  private def asciiControlChars: Gen[Char] = Gen.frequency(10 -> Gen.chooseNum(0, 31), 1 -> Gen.const(127)).map(_.toChar)

  // see https://en.wikipedia.org/wiki/Control_character#In_Unicode
  private def unicodeControlChars: Gen[Char] = Gen.choose(0x0080.toChar, 0x009f.toChar)

  private def emojis: Gen[Char] = Gen.oneOf(
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

  private def bcryptBase64Char: Gen[Char] = Gen.oneOf(
    Gen.choose('A', 'Z'),
    Gen.choose('a', 'z'),
    Gen.choose('0', '9'),
    Gen.const('.'),
    Gen.const('/')
  )
