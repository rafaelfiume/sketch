package org.fiume.sketch.shared.auth0.test

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.fiume.sketch.shared.auth0.Passwords.{HashedPassword, Salt}
import org.scalacheck.{Arbitrary, Gen}

trait PasswordGens:

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
