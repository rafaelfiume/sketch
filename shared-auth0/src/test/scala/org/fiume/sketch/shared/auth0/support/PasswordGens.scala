package org.fiume.sketch.shared.auth0.support

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.fiume.sketch.shared.auth0.Passwords.Salt
import org.scalacheck.{Arbitrary, Gen}

trait PasswordGens:

  given Arbitrary[Salt] = Arbitrary(salts)
  def salts: Gen[Salt] =
    given IORuntime = IORuntime.global
    Gen.delay(Salt.generate[IO]().unsafeRunSync())
