package org.fiume.sketch.auth0.http

import cats.effect.Async
import cats.implicits.*
import org.fiume.sketch.auth0.Authenticator
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo.ErrorMessage
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo.json.given
import org.fiume.sketch.shared.auth0.http.model.Login.{LoginRequestPayload, LoginResponsePayload}
import org.fiume.sketch.shared.auth0.http.model.Login.json.given
import org.http4s.{Challenge, HttpRoutes, Response, Status}
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`WWW-Authenticate`
import org.http4s.server.Router

class AuthRoutes[F[_]: Async](authenticator: Authenticator[F]) extends Http4sDsl[F]:
  private val prefix = "/"

  def router(): HttpRoutes[F] = Router(
    prefix -> httpRoutes
  )

  private val httpRoutes: HttpRoutes[F] = HttpRoutes.of[F] { case req @ POST -> Root / "login" =>
    req.decode { (login: LoginRequestPayload) =>
      for
        auth <- login.validated().flatMap { case (username, password) =>
          authenticator.authenticate(username, password)
        }
        resp <- auth match
          case Right(token) =>
            Ok(LoginResponsePayload(token.value))

          case Left(failure) =>
            Response[F](status = Status.Unauthorized)
              .putHeaders(`WWW-Authenticate`(Challenge("Bearer", "Authentication Service")))
              .withEntity(
                ErrorInfo.make(
                  // always the same error message for security reasons
                  ErrorMessage("The username or password provided is incorrect.")
                )
              )
              .pure[F]
      yield resp
    }
  }
