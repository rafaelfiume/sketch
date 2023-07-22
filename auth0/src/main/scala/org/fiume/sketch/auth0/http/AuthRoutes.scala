package org.fiume.sketch.auth0.http

import cats.data.NonEmptyChain
import cats.effect.Async
import cats.implicits.*
import io.circe.generic.auto.*
import org.fiume.sketch.auth0.Authenticator
import org.fiume.sketch.auth0.http.AuthRoutes.*
import org.fiume.sketch.auth0.http.AuthRoutes.Model.{LoginRequest, LoginResponse}
import org.fiume.sketch.auth0.http.JsonCodecs.RequestResponsesCodecs.given
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo.{ErrorCode, ErrorDetails, ErrorMessage}
import org.fiume.sketch.shared.app.troubleshooting.http.JsonCodecs.ErrorInfoCodecs.given
import org.fiume.sketch.shared.auth0.Passwords.PlainPassword
import org.fiume.sketch.shared.auth0.Passwords.PlainPassword.WeakPassword
import org.fiume.sketch.shared.auth0.User.Username
import org.fiume.sketch.shared.auth0.User.Username.WeakUsername
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
    def validate(request: LoginRequest) =
      (
        Username.validated(request.username).leftMap(weakUsernamesToErrorDetails),
        PlainPassword.validated(request.password).leftMap(weakPasswordsToErrorDetails)
      ).parMapN((_, _)).leftMap(ErrorDetails.apply)

    req.decode { (loginRequest: LoginRequest) =>
      logger.info(s"Attempt to authenticate username ${loginRequest.username}") *>
        validate(loginRequest).fold(
          inputErrors =>
            logger.info(s"(AUTH001) Failed login attempt for username ${loginRequest.username}") *>
              Async[F].delay(println(s"inputErrors: $inputErrors")) *>
              BadRequest(
                ErrorInfo.withDetails(
                  code = ErrorCode.InvalidCredentials,
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
                      code = ErrorCode.InvalidCredentials,
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
    case class LoginResponse(token: String)

  def weakUsernamesToErrorDetails(errors: NonEmptyChain[WeakUsername]): Map[String, String] =
    import org.fiume.sketch.shared.auth0.User.Username.*
    def toDetail(invalid: WeakUsername) =
      (invalid match
        case _: TooShort            => "username.too.short"
        case _: TooLong             => "username.too.long"
        case InvalidCharater        => "username.invalid.characters"
        case ReservedWords          => "username.reserved.words"
        case ExcessiveRepeatedChars => "username.excessive.repeated.characters"
      ) -> invalid.message
    errors.map(toDetail).toList.toMap

  def weakPasswordsToErrorDetails(errors: NonEmptyChain[WeakPassword]): Map[String, String] =
    import org.fiume.sketch.shared.auth0.Passwords.PlainPassword.*
    def toDetail(invalid: WeakPassword): (String, String) =
      (invalid match
        case _: TooShort        => "password.too.short"
        case _: TooLong         => "password.too.long"
        case NoUpperCase        => "password.no.uppercase"
        case NoLowerCase        => "password.no.lowercase"
        case NoDigit            => "password.no.digit"
        case NoSpecialChar      => "password.no.special.character"
        case InvalidSpecialChar => "password.invalid.special.character"
        case Whitespace         => "password.whitespace"
        case InvalidCharater    => "password.invalid.characters"
      ) -> invalid.message
    errors.map(toDetail).toList.toMap
