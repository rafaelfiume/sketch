package org.fiume.sketch.auth0.testkit

import cats.effect.IO
import cats.implicits.*
import org.fiume.sketch.auth0.{AuthenticationError, Authenticator, JwtError, JwtToken}
import org.fiume.sketch.auth0.AuthenticationError.*
import org.fiume.sketch.auth0.testkit.JwtTokenGens.*
import org.fiume.sketch.shared.auth0.Passwords.PlainPassword
import org.fiume.sketch.shared.auth0.User
import org.fiume.sketch.shared.auth0.User.Username
import org.scalacheck.Gen

trait AuthenticatorContext:
  def makeAuthenticator(signee: (User, PlainPassword), signeeAuthToken: JwtToken): IO[Authenticator[IO]] = IO.delay {
    new Authenticator[IO]:
      override def authenticate(username: Username, password: PlainPassword): IO[Either[AuthenticationError, JwtToken]] =
        if username != signee._1.username then UserNotFoundError.asLeft[JwtToken].pure[IO]
        else if password != signee._2 then InvalidPasswordError.asLeft[JwtToken].pure[IO]
        else IO.delay { signeeAuthToken.asRight }

      override def verify(jwtToken: JwtToken): Either[JwtError, User] =
        if signeeAuthToken == jwtToken then signee._1.asRight[JwtError]
        else JwtError.JwtInvalidTokenError(s"Expected $signeeAuthToken; got $jwtToken instead").asLeft[User]
  }

  def makeFailingAuthenticator(
    jwtError: JwtError = jwtErrors.sample.get
  ): IO[Authenticator[IO]] = IO.delay {
    new Authenticator[IO]:
      override def authenticate(username: Username, password: PlainPassword): IO[Either[AuthenticationError, JwtToken]] =
        IO.delay {
          Gen.oneOf(UserNotFoundError, InvalidPasswordError).sample.get.asLeft
        }

      override def verify(jwtToken: JwtToken): Either[JwtError, User] = jwtError.asLeft
  }
