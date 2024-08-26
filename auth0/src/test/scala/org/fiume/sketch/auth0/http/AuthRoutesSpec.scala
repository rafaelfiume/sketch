package org.fiume.sketch.auth0.http

import cats.effect.IO
import cats.implicits.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import munit.Assertions.*
import org.fiume.sketch.auth0.http.AuthRoutes.Model.{LoginRequestPayload, LoginResponsePayload}
import org.fiume.sketch.auth0.http.AuthRoutes.Model.json.given
import org.fiume.sketch.auth0.testkit.AuthenticatorContext
import org.fiume.sketch.auth0.testkit.JwtTokenGens.jwtTokens
import org.fiume.sketch.shared.app.http4s.middlewares.{SemanticInputError, SemanticValidationMiddleware}
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo.{ErrorDetails, ErrorMessage}
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo.json.given
import org.fiume.sketch.shared.auth0.Passwords.PlainPassword
import org.fiume.sketch.shared.auth0.Passwords.PlainPassword.WeakPasswordError
import org.fiume.sketch.shared.auth0.User
import org.fiume.sketch.shared.auth0.User.Username
import org.fiume.sketch.shared.auth0.User.Username.WeakUsernameError
import org.fiume.sketch.shared.auth0.testkit.PasswordsGens.*
import org.fiume.sketch.shared.auth0.testkit.UserGens.*
import org.fiume.sketch.shared.testkit.{ContractContext, Http4sTestingRoutesDsl}
import org.fiume.sketch.shared.testkit.Syntax.EitherSyntax.*
import org.fiume.sketch.shared.testkit.Syntax.StringSyntax.*
import org.http4s.Method.*
import org.http4s.Status
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.client.dsl.io.*
import org.http4s.implicits.*
import org.scalacheck.{Arbitrary, Gen, ShrinkLowPriority}
import org.scalacheck.effect.PropF.forAllF

class AuthRoutesSpec
    extends CatsEffectSuite
    with ScalaCheckEffectSuite
    with AuthRoutesSpecContext
    with AuthenticatorContext
    with Http4sTestingRoutesDsl
    with ContractContext
    with ShrinkLowPriority:

  test("valid login request results in a jwt token"):
    forAllF(loginRequests, jwtTokens) { case (user -> loginRequest -> jwtToken) =>
      val plainPassword = PlainPassword.notValidatedFromString(loginRequest.password)
      for
        authenticator <- makeAuthenticator(
          signee = user -> plainPassword,
          signeeAuthToken = jwtToken
        )

        request = POST(uri"/login").withEntity(loginRequest)
        jsonResponse <- send(request)
          .to(new AuthRoutes[IO](authenticator).router())
          .expectJsonResponseWith(Status.Ok)
//
      yield assertEquals(
        jsonResponse.as[LoginResponsePayload].map(_.token).rightValue,
        jwtToken.value
      )
    }

  test("login with wrong password fails with 401 Unauthorized status"):
    forAllF(loginRequests, jwtTokens) { case (user -> loginRequest -> jwtToken) =>
      val plainPassword = PlainPassword.notValidatedFromString(loginRequest.password)
      for
        authenticator <- makeAuthenticator(
          signee = user -> plainPassword,
          signeeAuthToken = jwtToken
        )

        request = POST(uri"/login").withEntity(
          loginRequest.withShuffledPassword
        )
        jsonResponse <- send(request)
          .to(new AuthRoutes[IO](authenticator).router())
          .expectJsonResponseWith(Status.Unauthorized)
//
      yield assertEquals(
        jsonResponse.as[ErrorInfo].rightValue,
        ErrorInfo.make(
          message = ErrorMessage("The username or password provided is incorrect.")
        )
      )
    }

  test("login with unknown username fails with 401 Unauthorized status"):
    forAllF(loginRequests, jwtTokens) { case (user -> loginRequest -> jwtToken) =>
      val plainPassword = PlainPassword.notValidatedFromString(loginRequest.password)
      for
        authenticator <- makeAuthenticator(
          signee = user -> plainPassword,
          signeeAuthToken = jwtToken
        )

        request = POST(uri"/login").withEntity(
          loginRequest.withShuffledUsername
        )
        jsonResponse <- send(request)
          .to(new AuthRoutes[IO](authenticator).router())
          .expectJsonResponseWith(Status.Unauthorized)
//
      yield assertEquals(
        jsonResponse.as[ErrorInfo].rightValue,
        ErrorInfo.make(
          message = ErrorMessage("The username or password provided is incorrect.")
        )
      )
    }

  test("login with semantically invalid username or password fails with 422 Unprocessable Entity"):
    forAllF(invalidInputs, jwtTokens) { case (user -> loginRequest -> jwtToken) =>
      val plainPassword = PlainPassword.notValidatedFromString(loginRequest.password)
      for
        authenticator <- makeAuthenticator(
          signee = user -> plainPassword,
          signeeAuthToken = jwtToken
        )

        request = POST(uri"/login").withEntity(loginRequest)
        result <- send(request)
          .to(SemanticValidationMiddleware(new AuthRoutes[IO](authenticator).router()))
          .expectJsonResponseWith(Status.UnprocessableEntity)
          .map(_.as[ErrorInfo].rightValue)

        _ <- IO {
          assertEquals(result.message, SemanticInputError.message)
          val allInputErrors = usernameInvariantErrors.map(_.uniqueCode) ++ plainPasswordInvariantErrors.map(_.uniqueCode)
          val actualInputErrors = result.details.get.tips.keys.toSet
          assert(actualInputErrors.subsetOf(allInputErrors),
                 clue = s"actualInputErrors: $actualInputErrors\nallInputErrors: $allInputErrors"
          )
        }
      yield ()
    }

  test("login request with malformed body fails with 422 Unprocessable Entity"):
    forAllF(malformedInputs) { badClientInput =>
      for
        authenticator <- makeFailingAuthenticator()

        request = POST(uri"/login").withEntity(badClientInput)
        result <- send(request)
          .to(SemanticValidationMiddleware(new AuthRoutes[IO](authenticator).router()))
          .expectJsonResponseWith(Status.UnprocessableEntity)
          .map(_.as[ErrorInfo].rightValue)

        _ <- IO {
          assertEquals(result.message, SemanticInputError.message)
          assertEquals(result.details, ErrorDetails("input.semantic.error" -> "The request body was invalid.").some)
        }
      yield ()
    }

  /*
   * ContractSpec
   */

  test("LoginResponsePayload encode and decode form a bijective relationship"):
    assertBijectiveRelationshipBetweenEncoderAndDecoder[LoginResponsePayload](
      "contract/auth0/http/login.success.json"
    )

trait AuthRoutesSpecContext:
  extension (loginRequest: LoginRequestPayload)
    def withShuffledPassword: LoginRequestPayload = loginRequest.copy(password = loginRequest.password._shuffled)
    def withShuffledUsername: LoginRequestPayload = loginRequest.copy(username = loginRequest.username._shuffled)

  given Arbitrary[(User, LoginRequestPayload)] = Arbitrary(loginRequests)
  def loginRequests: Gen[(User, LoginRequestPayload)] =
    for
      user <- users
      password <- plainPasswords
    yield user -> LoginRequestPayload(user.username.value, password)

  def invalidInputs: Gen[(User, LoginRequestPayload)] =
    for
      username <- oneOfUsernameInputErrors
      password <- oneOfPasswordInputErrors
      user <- users.map { _.copy(username = Username.notValidatedFromString(username)) }
    yield user -> LoginRequestPayload(username, password)

  def malformedInputs: Gen[String] =
    Gen.frequency(
      1 -> Gen.const("{\"unexpected\":\"payload\"}"),
      9 -> Gen.alphaNumStr
    )

  def usernameInvariantErrors = Set(
    WeakUsernameError.TooShort,
    WeakUsernameError.TooLong,
    WeakUsernameError.InvalidChar,
    WeakUsernameError.ReservedWords,
    WeakUsernameError.ExcessiveRepeatedChars
  )
  def plainPasswordInvariantErrors =
    Set(
      WeakPasswordError.TooShort,
      WeakPasswordError.TooLong,
      WeakPasswordError.NoUpperCase,
      WeakPasswordError.NoLowerCase,
      WeakPasswordError.NoDigit,
      WeakPasswordError.NoSpecialChar,
      WeakPasswordError.InvalidSpecialChar,
      WeakPasswordError.Whitespace,
      WeakPasswordError.InvalidChar
    )
