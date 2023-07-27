package org.fiume.sketch.auth0.http

import cats.effect.Async
import cats.implicits.*
import io.circe.generic.auto.*
import org.fiume.sketch.auth0.Authenticator
import org.fiume.sketch.auth0.http.AuthRoutes.*
import org.fiume.sketch.auth0.http.AuthRoutes.Model.{LoginRequest, LoginResponse}
import org.fiume.sketch.auth0.http.JsonCodecs.RequestResponsesCodecs.given
import org.fiume.sketch.shared.app.troubleshooting.{ErrorInfo, InvariantError}
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo.{ErrorCode, ErrorDetails, ErrorMessage}
import org.fiume.sketch.shared.app.troubleshooting.http.JsonCodecs.ErrorInfoCodecs.given
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
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

class AuthRoutes[F[_]: Async](authenticator: Authenticator[F]) extends Http4sDsl[F]:
  private val logger = Slf4jLogger.getLogger[F]

  private val prefix = "/"

  def router(): HttpRoutes[F] = Router(prefix -> httpRoutes)

  private val httpRoutes: HttpRoutes[F] = HttpRoutes.of[F] { case req @ POST -> Root / "login" =>
    req.decode {
      (loginRequest: LoginRequest) => // TODO What's the error returned when the request is not JSON or it's an invalid JSON?
        logger.info(s"Attempt to authenticate username ${loginRequest.username}") *>
          loginRequest
            .validated()
            .fold(
              inputErrors =>
                logger.info(s"(AUTH001) Failed login attempt for username ${loginRequest.username}") *>
                  BadRequest(
                    ErrorInfo.withDetails(
                      code = ErrorCode.InvalidUserCredentials,
                      message = ErrorMessage("The username or password provided is incorrect."),
                      details = inputErrors
                    )
                  ),
              (username, password) =>
                authenticator.authenticate(username, password).flatMap {
                  case Right(token) =>
                    logger.info(s"Successful login attempt for username ${loginRequest.username}") *>
                      Ok(LoginResponse(token.value))
                  case Left(failure) =>
                    logger.info(s"(AUTH002) Failed login attempt for username ${loginRequest.username}") *>
                      Ok(
                        ErrorInfo(
                          code = ErrorCode.InvalidUserCredentials,
                          message = ErrorMessage("The username or password provided is incorrect.")
                        )
                      )
                }
            )
    }
  }

object AuthRoutes:
  object Model:

    case class LoginRequest(username: String, password: String)

    object LoginRequest:
      extension (request: LoginRequest)
        def validated(): Either[ErrorDetails, (Username, PlainPassword)] =
          (
            Username.validated(request.username).leftMap(_.toList).leftMap(InvariantError.inputErrorsToMap),
            PlainPassword.validated(request.password).leftMap(_.toList).leftMap(InvariantError.inputErrorsToMap),
          ).parMapN((_, _)).leftMap(ErrorDetails.apply)

    case class LoginResponse(token: String)
