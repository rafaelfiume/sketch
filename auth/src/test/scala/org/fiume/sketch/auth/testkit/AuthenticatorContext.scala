package org.fiume.sketch.auth.testkit

import cats.effect.IO
import cats.implicits.*
import org.fiume.sketch.auth.Authenticator
import org.fiume.sketch.shared.auth.domain.{AuthenticationError, Jwt, JwtError, User}
import org.fiume.sketch.shared.auth.domain.AuthenticationError.*
import org.fiume.sketch.shared.auth.domain.Passwords.PlainPassword
import org.fiume.sketch.shared.auth.domain.User.Username
import org.fiume.sketch.shared.auth.testkit.JwtGens.*
import org.fiume.sketch.shared.testkit.syntax.OptionSyntax.*
import org.scalacheck.Gen

trait AuthenticatorContext:
  def makeAuthenticator(signee: (User, PlainPassword), signeeAuthToken: Jwt): IO[Authenticator[IO]] = IO.delay {
    new Authenticator[IO]:
      override def authenticate(username: Username, password: PlainPassword): IO[Either[AuthenticationError, Jwt]] =
        if username != signee._1.username then UserNotFoundError.asLeft[Jwt].pure[IO]
        else if password != signee._2 then InvalidPasswordError.asLeft[Jwt].pure[IO]
        else IO.delay { signeeAuthToken.asRight }

      override def verify(jwt: Jwt): Either[JwtError, User] =
        if signeeAuthToken == jwt then signee._1.asRight[JwtError]
        else JwtError.JwtInvalidTokenError(s"Expected $signeeAuthToken; got $jwt instead").asLeft[User]
  }

  def makeFailingAuthenticator(jwtError: JwtError = jwtErrors.sample.someOrFail): IO[Authenticator[IO]] = IO.delay {
    new Authenticator[IO]:
      override def authenticate(username: Username, password: PlainPassword): IO[Either[AuthenticationError, Jwt]] =
        IO.delay {
          Gen.oneOf(UserNotFoundError, InvalidPasswordError).sample.someOrFail.asLeft
        }

      override def verify(jwt: Jwt): Either[JwtError, User] = jwtError.asLeft
  }