package org.fiume.sketch.auth.http

import cats.effect.IO
import cats.implicits.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import munit.Assertions.*
import org.fiume.sketch.auth.testkit.AuthenticatorContext
import org.fiume.sketch.shared.app.http4s.middlewares.SemanticValidationMiddleware
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo.{ErrorCode, ErrorDetails, ErrorMessage}
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo.json.given
import org.fiume.sketch.shared.auth.domain.Passwords.PlainPassword
import org.fiume.sketch.shared.auth.domain.Passwords.PlainPassword.WeakPasswordError
import org.fiume.sketch.shared.auth.domain.User
import org.fiume.sketch.shared.auth.domain.User.Username
import org.fiume.sketch.shared.auth.domain.User.Username.WeakUsernameError
import org.fiume.sketch.shared.auth.http.model.Login.{LoginRequestPayload, LoginResponsePayload}
import org.fiume.sketch.shared.auth.http.model.Login.json.given
import org.fiume.sketch.shared.auth.testkit.JwtGens.jwts
import org.fiume.sketch.shared.auth.testkit.PasswordsGens.*
import org.fiume.sketch.shared.auth.testkit.UserGens.*
import org.fiume.sketch.shared.testkit.{ContractContext, Http4sRoutesContext}
import org.fiume.sketch.shared.testkit.syntax.OptionSyntax.*
import org.fiume.sketch.shared.testkit.syntax.StringSyntax.*
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
    with Http4sRoutesContext
    with ContractContext
    with ShrinkLowPriority:

  test("valid login request results in a jwt"):
    forAllF(loginRequests, jwts) { case (user -> loginRequest -> jwt) =>
      val plainPassword = PlainPassword.makeUnsafeFromString(loginRequest.password)
      for
        authenticator <- makeAuthenticator(
          signee = user -> plainPassword,
          signeeAuthToken = jwt
        )

        request = POST(uri"/login").withEntity(loginRequest)
        result <- send(request)
          .to(new AuthRoutes[IO](authenticator).router())
          .expectJsonResponseWith[LoginResponsePayload](Status.Ok)
//
      yield assertEquals(result.token, jwt.value)
    }

  test("login with wrong password fails with 401 Unauthorized status"):
    forAllF(loginRequests, jwts) { case (user -> loginRequest -> jwt) =>
      val plainPassword = PlainPassword.makeUnsafeFromString(loginRequest.password)
      for
        authenticator <- makeAuthenticator(
          signee = user -> plainPassword,
          signeeAuthToken = jwt
        )

        request = POST(uri"/login").withEntity(
          loginRequest.withShuffledPassword
        )
        result <- send(request)
          .to(new AuthRoutes[IO](authenticator).router())
          .expectJsonResponseWith[ErrorInfo](Status.Unauthorized)
//
      yield assertEquals(
        result,
        ErrorInfo.make(
          ErrorCode("1002"),
          ErrorMessage("Attempt to login failed")
        )
      )
    }

  test("login with unknown username fails with 401 Unauthorized status"):
    forAllF(loginRequests, jwts) { case (user -> loginRequest -> jwt) =>
      val plainPassword = PlainPassword.makeUnsafeFromString(loginRequest.password)
      for
        authenticator <- makeAuthenticator(
          signee = user -> plainPassword,
          signeeAuthToken = jwt
        )

        request = POST(uri"/login").withEntity(
          loginRequest.withShuffledUsername
        )
        result <- send(request)
          .to(new AuthRoutes[IO](authenticator).router())
          .expectJsonResponseWith[ErrorInfo](Status.Unauthorized)
//
      yield assertEquals(
        result,
        ErrorInfo.make(
          ErrorCode("1001"),
          ErrorMessage("Attempt to login failed")
        )
      )
    }

  test("login with semantically invalid username or password fails with 422 Unprocessable Entity"):
    forAllF(invalidInputs, jwts) { case (user -> loginRequest -> jwt) =>
      val plainPassword = PlainPassword.makeUnsafeFromString(loginRequest.password)
      for
        authenticator <- makeAuthenticator(
          signee = user -> plainPassword,
          signeeAuthToken = jwt
        )

        request = POST(uri"/login").withEntity(loginRequest)
        result <- send(request)
          .to(SemanticValidationMiddleware(new AuthRoutes[IO](authenticator).router()))
          .expectJsonResponseWith[ErrorInfo](Status.UnprocessableEntity)

        _ <- IO {
          assertEquals(result.code, ErrorCode("1000"))
          assertEquals(result.message, ErrorMessage("Invalid username or password"))
          val allInputErrors = usernameInvariantErrors.map(_.key) |+| plainPasswordInvariantErrors.map(_.key)
          val actualInputErrors = result.details.someOrFail.tips.keys.toSet
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
          .expectJsonResponseWith[ErrorInfo](Status.UnprocessableEntity)

//
      yield
        assertEquals(result.message, ErrorMessage("The request body could not be processed"))
        assertEquals(result.details, ErrorDetails("input.semantic.error" -> "The request body was invalid.").some)
    }

  test("LoginRequestPayload encode and decode form a bijective relationship"):
    assertBijectiveRelationshipBetweenEncoderAndDecoder[LoginRequestPayload]("auth/login/post.request.json")

  test("LoginResponsePayload encode and decode form a bijective relationship"):
    assertBijectiveRelationshipBetweenEncoderAndDecoder[LoginResponsePayload]("auth/login/post.response.json")

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
      user <- users.map { _.copy(username = Username.makeUnsafeFromString(username)) }
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
