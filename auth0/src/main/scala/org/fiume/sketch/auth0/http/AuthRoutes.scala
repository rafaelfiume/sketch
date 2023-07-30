package org.fiume.sketch.auth0.http

import cats.effect.Async
import cats.implicits.*
import io.circe.generic.auto.*
import org.fiume.sketch.auth0.Authenticator
import org.fiume.sketch.auth0.http.AuthRoutes.*
import org.fiume.sketch.auth0.http.AuthRoutes.Model.{LoginRequestPayload, LoginResponsePayload}
import org.fiume.sketch.auth0.http.PayloadCodecs.Login.given
import org.fiume.sketch.shared.app.http4s.middlewares.{ErrorInfoMiddleware, SemanticInputError, TraceAuditLogMiddleware}
import org.fiume.sketch.shared.app.troubleshooting.{ErrorCode, ErrorDetails, ErrorInfo, ErrorMessage, InvariantError}
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo.given
import org.fiume.sketch.shared.app.troubleshooting.InvariantErrorSyntax.asDetails
import org.fiume.sketch.shared.app.troubleshooting.http.PayloadCodecs.ErrorInfoCodecs.given
import org.fiume.sketch.shared.auth0.Passwords.PlainPassword
import org.fiume.sketch.shared.auth0.Passwords.PlainPassword.WeakPasswordError
import org.fiume.sketch.shared.auth0.User.Username
import org.fiume.sketch.shared.auth0.User.Username.WeakUsernameError
import org.http4s.{Challenge, HttpRoutes, Response}
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`WWW-Authenticate`
import org.http4s.server.Router
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.util.control.NoStackTrace

class AuthRoutes[F[_]: Async](enableLogging: Boolean)(authenticator: Authenticator[F]) extends Http4sDsl[F]:
  private val prefix = "/"

  def router(): HttpRoutes[F] = Router(
    prefix -> TraceAuditLogMiddleware(Slf4jLogger.getLogger[F], enableLogging)(ErrorInfoMiddleware(httpRoutes))
  )

  private val httpRoutes: HttpRoutes[F] = HttpRoutes.of[F] { case req @ POST -> Root / "login" =>
    req.decode { (login: LoginRequestPayload) =>
      for
        auth <- login.validated().flatMap { case (username, password) =>
          authenticator.authenticate(username, password)
        }
        resp <- auth match
          case Right(token) => Ok(LoginResponsePayload(token.value))
          case Left(failure) =>
            Ok(
              ErrorInfo.short(
                ErrorCode.InvalidUserCredentials,
                ErrorMessage("The username or password provided is incorrect.")
              )
            )
      yield resp
    }
  }

private[http] object AuthRoutes:
  object Model:

    case class LoginRequestPayload(username: String, password: String)

    object LoginRequestPayload:
      extension (payload: LoginRequestPayload)
        def validated[F[_]: Async](): F[(Username, PlainPassword)] =
          (
            Username.validated(payload.username).leftMap(_.asDetails),
            PlainPassword.validated(payload.password).leftMap(_.asDetails),
          ).parMapN((_, _))
            .fold(
              errorDetails =>
                SemanticInputError(
                  ErrorCode.InvalidClientInput,
                  ErrorMessage("The username or password provided is incorrect."),
                  errorDetails
                ).raiseError,
              _.pure[F]
            )

    case class LoginResponsePayload(token: String)
