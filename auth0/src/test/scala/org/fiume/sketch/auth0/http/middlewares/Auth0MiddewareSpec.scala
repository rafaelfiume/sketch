package org.fiume.sketch.auth0.http.middlewares

import cats.effect.IO
import io.circe.Json
import io.circe.parser.parse
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import munit.Assertions.*
import org.fiume.sketch.auth0.{JwtError, JwtToken}
import org.fiume.sketch.auth0.testkit.AuthenticatorContext
import org.fiume.sketch.auth0.testkit.JwtTokenGens.given
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo.{ErrorDetails, ErrorMessage}
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo.json.given
import org.fiume.sketch.shared.auth0.Passwords.PlainPassword
import org.fiume.sketch.shared.auth0.User
import org.fiume.sketch.shared.auth0.testkit.PasswordsGens.given
import org.fiume.sketch.shared.auth0.testkit.UserGens.given
import org.fiume.sketch.shared.testkit.Syntax.EitherSyntax.*
import org.http4s.{AuthedRoutes, Headers, Response, Status}
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.client.dsl.io.*
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Authorization
import org.http4s.implicits.*
import org.scalacheck.ShrinkLowPriority
import org.scalacheck.effect.PropF.forAllF

class Auth0MiddlewareSpec
    extends CatsEffectSuite
    with ScalaCheckEffectSuite
    with AuthenticatorContext
    with Http4sDsl[IO]
    with ShrinkLowPriority:

  test("access with a valid token is allowed"):
    forAllF { (user: User, plainPassword: PlainPassword, jwtToken: JwtToken) =>
      for
        authenticator <- makeAuthenticator(signee = (user, plainPassword), signeeAuthToken = jwtToken)
        routeResponsePayload = parse(s"""{"a.property" : "${user.uuid}" }""").rightValue
        authedRoutes = AuthedRoutes.of[User, IO] { case GET -> Root / "user" as user => Ok(routeResponsePayload) }

        middleware = Auth0Middleware(authenticator)
        request = GET(uri"/user").withHeaders(Headers(Authorization.parse(s"Bearer ${jwtToken.value}")))
        response <- middleware(authedRoutes).orNotFound.run(request)

        _ <- response.as[Json].flatMap { result =>
          IO {
            assertEquals(response.status, Status.Ok)
            assertEquals(result, routeResponsePayload)
          }
        }
      yield ()
    }

  test("attempt to access with an invalid token is rejected"):
    forAllF { (user: User, jwtToken: JwtToken, jwtError: JwtError) =>
      for
        authenticator <- makeFailingAuthenticator(jwtError)
        authedRoutes = AuthedRoutes.of[User, IO] { case GET -> Root / "user" as user => Ok(user.toString) }

        middleware = Auth0Middleware(authenticator)
        request = GET(uri"/user").withHeaders(Headers(Authorization.parse(s"Bearer ${jwtToken.value}")))
        response <- middleware(authedRoutes).orNotFound.run(request)

        _ <- response.as[ErrorInfo].flatMap { result =>
          IO {
            assertEquals(response.status, Status.Unauthorized)
            assertEquals(
              result,
              ErrorInfo.make(
                ErrorMessage("Invalid credentials"),
                ErrorDetails("invalid.jwt" -> jwtError.toString)
              )
            )
          }
        }
      yield ()
    }
