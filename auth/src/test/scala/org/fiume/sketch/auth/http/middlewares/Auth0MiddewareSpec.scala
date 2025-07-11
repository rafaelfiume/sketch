package org.fiume.sketch.auth.http.middlewares

import cats.effect.IO
import io.circe.Json
import io.circe.parser.parse
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.fiume.sketch.auth.testkit.AuthenticatorContext
import org.fiume.sketch.shared.auth.{Jwt, JwtVerificationError, User}
import org.fiume.sketch.shared.auth.Passwords.PlainPassword
import org.fiume.sketch.shared.auth.testkit.JwtGens.given
import org.fiume.sketch.shared.auth.testkit.PasswordsGens.given
import org.fiume.sketch.shared.auth.testkit.UserGens.given
import org.fiume.sketch.shared.common.troubleshooting.ErrorInfo
import org.fiume.sketch.shared.common.troubleshooting.ErrorInfo.json.given
import org.fiume.sketch.shared.common.troubleshooting.syntax.ErrorInfoSyntax.*
import org.fiume.sketch.shared.testkit.syntax.EitherSyntax.*
import org.http4s.{AuthedRoutes, Headers, Status}
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
    forAllF { (user: User, plainPassword: PlainPassword, jwt: Jwt) =>
      for
        authenticator <- makeAuthenticator(signee = (user, plainPassword), signeeAuthToken = jwt)
        routeResponsePayload = parse(s"""{"a.property" : "${user.uuid}" }""").rightOrFail
        authedRoutes = AuthedRoutes.of[User, IO] { case GET -> Root / "user" as _ => Ok(routeResponsePayload) }

        middleware = Auth0Middleware(authenticator)
        request = GET(uri"/user").withHeaders(Headers(Authorization.parse(s"Bearer ${jwt.value}")))
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
    forAllF { (jwt: Jwt, jwtError: JwtVerificationError) =>
      for
        authenticator <- makeFailingAuthenticator(jwtError)
        authedRoutes = AuthedRoutes.of[User, IO] { case GET -> Root / "user" as user => Ok(user.toString) }

        middleware = Auth0Middleware(authenticator)
        request = GET(uri"/user").withHeaders(Headers(Authorization.parse(s"Bearer ${jwt.value}")))
        response <- middleware(authedRoutes).orNotFound.run(request)

        payload <- response.as[ErrorInfo]
      yield
        assertEquals(response.status, Status.Unauthorized)
        assertEquals(
          payload,
          ErrorInfo.make("1011".code, "Invalid credentials".message, ("invalid.jwt" -> jwtError.toString).details)
        )
    }
