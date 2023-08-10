package org.fiume.sketch.auth0.testkit

import cats.effect.IO
import cats.implicits.*
import munit.Assertions.*
import org.fiume.sketch.auth0.{AuthenticationError, Authenticator, JwtError, JwtToken}
import org.fiume.sketch.auth0.AuthenticationError.*
import org.fiume.sketch.shared.auth0.Passwords.PlainPassword
import org.fiume.sketch.shared.auth0.User
import org.fiume.sketch.shared.auth0.User.Username

trait AuthenticatorContext:
  def makeAuthenticator(signee: (User, PlainPassword), signeeAuthToken: JwtToken): IO[Authenticator[IO]] = IO.delay {
    new Authenticator[IO]:
      override def authenticate(username: Username, password: PlainPassword): IO[Either[AuthenticationError, JwtToken]] =
        if username != signee._1.username then UserNotFoundError.asLeft[JwtToken].pure[IO]
        else if password != signee._2 then InvalidPasswordError.asLeft[JwtToken].pure[IO]
        else IO.delay { signeeAuthToken.asRight }

      // TODO verify
      override def verify(jwtToken: JwtToken): Either[JwtError, User] =
        signee._1.asRight[JwtError]
  }

  def makeFailingAuthenticator(): IO[Authenticator[IO]] = IO.delay {
    new Authenticator[IO]:
      override def authenticate(username: Username, password: PlainPassword): IO[Either[AuthenticationError, JwtToken]] =
        IO.delay { fail("authenticate should have not been invoked") }

      override def verify(jwtToken: JwtToken): Either[JwtError, User] =
        fail("verify should have not been invoked")
  }
