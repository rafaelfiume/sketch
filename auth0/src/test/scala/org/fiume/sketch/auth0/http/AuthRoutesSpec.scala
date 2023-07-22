package org.fiume.sketch.auth0.http

import cats.effect.IO
import cats.implicits.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.fiume.sketch.auth0.{AuthenticationError, Authenticator, InvalidPasswordError, JwtToken, UserNotFoundError}
import org.fiume.sketch.auth0.http.AuthRoutes.Model.{LoginRequest, LoginResponse}
import org.fiume.sketch.auth0.http.JsonCodecs.RequestResponsesCodecs.given
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo.{ErrorCode, ErrorDetails, ErrorMessage}
import org.fiume.sketch.shared.app.troubleshooting.http.JsonCodecs.ErrorInfoCodecs.given
import org.fiume.sketch.shared.auth0.Passwords.PlainPassword
import org.fiume.sketch.shared.auth0.User
import org.fiume.sketch.shared.auth0.User.Username
import org.fiume.sketch.shared.auth0.test.PasswordsGens.PlainPasswords.*
import org.fiume.sketch.shared.auth0.test.UserGens.*
import org.fiume.sketch.shared.auth0.test.UserGens.Usernames.*
import org.fiume.sketch.shared.test.{ContractContext, Http4sTestingRoutesDsl}
import org.fiume.sketch.shared.test.EitherSyntax.*
import org.fiume.sketch.shared.test.StringSyntax.*
import org.http4s.Method.*
import org.http4s.Status
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.client.dsl.io.*
import org.http4s.implicits.*
import org.scalacheck.{Arbitrary, Gen, ShrinkLowPriority}
import org.scalacheck.effect.PropF.forAllF

import scala.util.Random

class AuthRoutesSpec
    extends CatsEffectSuite
    with ScalaCheckEffectSuite
    with AuthRoutesSpecContext
    with AuthenticatorContext
    with Http4sTestingRoutesDsl
    with ContractContext
    with ShrinkLowPriority:

  test("return a Jwt token for a valid loging request"):
    forAllF(loginRequests, authTokens) { case (user -> loginRequest -> authToken) =>
      val plainPassword = PlainPassword.notValidatedFromString(loginRequest.password)
      for
        authenticator <- makeAuthenticator(
          signee = user -> plainPassword,
          signeeAuthToken = authToken
        )

        request = POST(uri"/login").withEntity(loginRequest)
        jsonResponse <- send(request)
          .to(new AuthRoutes[IO](authenticator).router())
          .expectJsonResponseWith(Status.Ok)

        _ <- IO {
          assertEquals(
            jsonResponse.as[LoginResponse].map(_.token).rightValue,
            authToken.value
          )
        }
      yield ()
    }

  test("return error for a login request with wrong password"):
    forAllF(loginRequests, authTokens) { case (user -> loginRequest -> authToken) =>
      val plainPassword = PlainPassword.notValidatedFromString(loginRequest.password)
      for
        authenticator <- makeAuthenticator(
          signee = user -> plainPassword,
          signeeAuthToken = authToken
        )

        request = POST(uri"/login").withEntity(
          loginRequest.withShuffledPassword
        )
        jsonResponse <- send(request)
          .to(new AuthRoutes[IO](authenticator).router())
          .expectJsonResponseWith(Status.Ok)

        _ <- IO {
          assertEquals(
            jsonResponse.as[ErrorInfo].rightValue,
            ErrorInfo(
              code = ErrorCode.InvalidCredentials,
              message = ErrorMessage("The username or password provided is incorrect.")
            )
          )
        }
      yield ()
    }

  test("return error for a login request with unknown username"):
    forAllF(loginRequests, authTokens) { case (user -> loginRequest -> authToken) =>
      val plainPassword = PlainPassword.notValidatedFromString(loginRequest.password)
      for
        authenticator <- makeAuthenticator(
          signee = user -> plainPassword,
          signeeAuthToken = authToken
        )

        request = POST(uri"/login").withEntity(
          loginRequest.withShuffledUsername
        )
        jsonResponse <- send(request)
          .to(new AuthRoutes[IO](authenticator).router())
          .expectJsonResponseWith(Status.Ok)

        _ <- IO {
          assertEquals(
            jsonResponse.as[ErrorInfo].rightValue,
            ErrorInfo(
              code = ErrorCode.InvalidCredentials,
              message = ErrorMessage("The username or password provided is incorrect.")
            )
          )
        }
      yield ()
    }

  test("return an error for a login request with an invalid username or password".ignore):
    def invalids: Gen[(User, LoginRequest)] =
      for
        user <- users
        // TODO Combined errors
        username <- Gen.oneOf(
          shortUsernames,
          longUsernames,
          usernamesWithInvalidChars,
          usernamesWithReservedWords,
          usernamesWithRepeatedChars
        )
        password <- Gen.oneOf(
          shortPasswords,
          longPasswords,
          invalidPasswordsWithoutUppercase,
          invalidPasswordsWithoutLowercase,
          invalidPasswordsWithoutDigit,
          invalidPasswordsWithoutSpecialChar,
          invalidPasswordsWithWhitespace,
          invalidPasswordsWithInvalidSpecialChars,
          passwordsWithControlCharsOrEmojis
        )
      yield user.copy(username = Username.notValidatedFromString(username)) -> LoginRequest(username, password)

    forAllF(invalids, authTokens) { case (user -> loginRequest -> authToken) =>
      val plainPassword = PlainPassword.notValidatedFromString(loginRequest.password)
      for
        authenticator <- makeAuthenticator(
          signee = user -> plainPassword,
          signeeAuthToken = authToken
        )

        request = POST(uri"/login").withEntity(loginRequest)
        jsonResponse <- send(request)
          .to(new AuthRoutes[IO](authenticator).router())
          .expectJsonResponseWith(Status.BadRequest)

        _ <- IO {
          assertEquals(
            jsonResponse.as[ErrorInfo].rightValue,
            ErrorInfo(
              code = ErrorCode.InvalidCredentials,
              message = ErrorMessage("The username or password provided is incorrect.")
            )
          )
        }
      yield ()
    }

  /*
   * ContractSpec
   */

  test("bijective relationship between decoded and encoded LoginResponse"):
    assertBijectiveRelationshipBetweenEncoderAndDecoder[LoginResponse](
      "contract/auth0/http/login.success.json"
    )

trait AuthRoutesSpecContext:
  extension (loginRequest: LoginRequest)
    def withShuffledPassword: LoginRequest = loginRequest.copy(password = loginRequest.password._shuffled)
    def withShuffledUsername: LoginRequest = loginRequest.copy(username = loginRequest.username._shuffled)

  given Arbitrary[(User, LoginRequest)] = Arbitrary(loginRequests)
  def loginRequests: Gen[(User, LoginRequest)] =
    for
      user <- users
      password <- plainPasswords
    yield user -> LoginRequest(user.username.value, password)

  given Arbitrary[JwtToken] = Arbitrary(authTokens)
  def authTokens: Gen[JwtToken] = Gen
    .const(
      "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"
    )
    .map(JwtToken.notValidatedFromString)

trait AuthenticatorContext:
  def makeAuthenticator(signee: (User, PlainPassword), signeeAuthToken: JwtToken): IO[Authenticator[IO]] = IO.delay {
    new Authenticator[IO]:
      override def authenticate(
        username: Username,
        password: PlainPassword
      ): IO[Either[AuthenticationError, JwtToken]] =
        if username != signee._1.username then UserNotFoundError.asLeft[JwtToken].pure[IO]
        else if password != signee._2 then InvalidPasswordError.asLeft[JwtToken].pure[IO]
        else IO.delay { signeeAuthToken.asRight }

      // TODO verify
      override def verify(jwtToken: JwtToken): Either[AuthenticationError, User] =
        signee._1.asRight[AuthenticationError]
  }
