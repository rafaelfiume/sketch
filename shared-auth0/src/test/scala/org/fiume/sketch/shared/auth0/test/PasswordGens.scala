package org.fiume.sketch.shared.auth0.test

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.fiume.sketch.shared.auth0.Passwords.{HashedPassword, PlainPassword, Salt}
import org.fiume.sketch.shared.auth0.test.PasswordsGens.PlainPasswords.*
import org.fiume.sketch.shared.auth0.test.PasswordsGens.Salts.*
import org.scalacheck.{Arbitrary, Gen}

import scala.util.Random

object PasswordsGens:

  object PlainPasswords:
    given Arbitrary[PlainPassword] = Arbitrary(plainPasswords)
    def plainPasswords: Gen[PlainPassword] =
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
      yield PlainPassword.notValidatedFromString(password)) :| "valid passwords"

    def shortPasswords: Gen[PlainPassword] =
      (for
        password <- plainPasswords
        shortSize <- Gen.choose(0, PlainPassword.minLength - 1)
      yield password.modify(_.take(shortSize))) :| "short passwords"

    def longPasswords: Gen[PlainPassword] =
      (for
        password <- plainPasswords
        extraSize <- Gen.choose(PlainPassword.maxLength, 100)
        extraChars <- Gen.listOfN(extraSize, Gen.alphaNumChar).map(_.mkString)
      yield password.modify(_ + extraChars)) :| "long passwords"

    def invalidPasswordsWithoutUppercase: Gen[PlainPassword] =
      plainPasswords.map(_.modify(_.toLowerCase())) :| "passwords without uppercase"

    def invalidPasswordsWithoutLowercase: Gen[PlainPassword] =
      plainPasswords.map(_.modify(_.toUpperCase)) :| "passwords without lowercase"

    def invalidPasswordsWithoutDigit: Gen[PlainPassword] =
      plainPasswords.map(_.modify(_.filterNot(_.isDigit))) :| "passwords without digit"

    def invalidPasswordsWithoutSpecialChar: Gen[PlainPassword] =
      plainPasswords.map(_.modify(_.filter(_.isLetterOrDigit))) :| "passwords without special character"

    def invalidPasswordsWithWhitespace: Gen[PlainPassword] =
      (for
        password <- plainPasswords
        whitespace <- whitespaces
      yield password.modify(whitespace +: _).modify(Random.shuffle(_).mkString)) :| "passwords with whitespace"

    def invalidPasswordsWithInvalidSpecialChars: Gen[PlainPassword] =
      (for
        password <- plainPasswords
        invalidChar <- invalidSpecialChars
      yield password.modify(invalidChar +: _).modify(Random.shuffle(_).mkString)) :| "passwords with invalid special character"

    def passwordsWithControlCharsOrEmojis: Gen[PlainPassword] =
      (for
        password <- plainPasswords
        invalidChar <- invalidChars
      yield password.modify(invalidChar +: _).modify(Random.shuffle(_).mkString)) :| "passwords with control chars or emojis"

    def whitespaces: Gen[Char] = Gen.oneOf(' ', '\t', '\n', '\r')

    def invalidSpecialChars: Gen[Char] = Gen.oneOf(PlainPassword.invalidSpecialChars)

    def invalidChars: Gen[Char] = Gen.oneOf(asciiControlChars, unicodeControlChars, emojis)

    def asciiControlChars: Gen[Char] = Gen.frequency(10 -> Gen.chooseNum(0, 31), 1 -> Gen.const(127)).map(_.toChar)

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

    // TODO MOve it to a separated package?
    extension (password: PlainPassword)
      def modify(f: String => String): PlainPassword =
        PlainPassword.notValidatedFromString(f(password.value))

  object Salts:
    given Arbitrary[Salt] = Arbitrary(salts)
    def salts: Gen[Salt] =
      given IORuntime = IORuntime.global
      Gen.delay(Salt.generate[IO]().unsafeRunSync())

  object HashedPasswords:
    // a bcrypt hash approximation for efficience (store assumes correctness)
    given Arbitrary[HashedPassword] = Arbitrary(fakeHashedPasswords)
    def fakeHashedPasswords: Gen[HashedPassword] =
      Gen.listOfN(60, bcryptBase64Char).map(_.mkString).map(HashedPassword.notValidatedFromString)

    def passwordsInfo: Gen[(PlainPassword, HashedPassword, Salt)] =
      for
        plainPassword <- plainPasswords
        salt <- salts
        hashedPassword = HashedPassword.hashPassword(plainPassword, salt)
      yield (plainPassword, hashedPassword, salt)

    private def bcryptBase64Char: Gen[Char] = Gen.oneOf(
      Gen.choose('A', 'Z'),
      Gen.choose('a', 'z'),
      Gen.choose('0', '9'),
      Gen.const('.'),
      Gen.const('/')
    )
