package org.fiume.sketch.shared.auth0.test

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.fiume.sketch.shared.auth0.Passwords.{HashedPassword, PlainPassword, Salt}
import org.scalacheck.{Arbitrary, Gen}

trait PasswordsGens:

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

  given Arbitrary[Salt] = Arbitrary(salts)
  def salts: Gen[Salt] =
    given IORuntime = IORuntime.global
    Gen.delay(Salt.generate[IO]().unsafeRunSync())

  given Arbitrary[HashedPassword] = Arbitrary(hashedPasswords)
  def hashedPasswords: Gen[HashedPassword] =
    // a bcrypt hash approximation for efficience (store assumes correctness)
    Gen.listOfN(60, bcryptBase64Char).map(_.mkString).map(HashedPassword.unsafeFromString)

  private def bcryptBase64Char: Gen[Char] = Gen.oneOf(
    Gen.choose('A', 'Z'),
    Gen.choose('a', 'z'),
    Gen.choose('0', '9'),
    Gen.const('.'),
    Gen.const('/')
  )
