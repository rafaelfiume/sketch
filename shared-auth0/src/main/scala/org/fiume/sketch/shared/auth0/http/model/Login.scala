package org.fiume.sketch.shared.auth0.http.model

import cats.effect.Async
import cats.implicits.*
import org.fiume.sketch.shared.app.http4s.middlewares.SemanticInputError
import org.fiume.sketch.shared.app.troubleshooting.{ErrorCode, ErrorInfo}
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo.{ErrorDetails, ErrorMessage}
import org.fiume.sketch.shared.app.troubleshooting.InvariantErrorSyntax.asDetails
import org.fiume.sketch.shared.auth0.domain.AuthenticationError
import org.fiume.sketch.shared.auth0.domain.AuthenticationError.*
import org.fiume.sketch.shared.auth0.domain.Passwords.PlainPassword
import org.fiume.sketch.shared.auth0.domain.User.Username

object Login:

  case class LoginRequestPayload(username: String, password: String)
  case class LoginResponsePayload(token: String)

  extension (payload: LoginRequestPayload)
    def validated[F[_]: Async](): F[(Username, PlainPassword)] =
      (
        Username.validated(payload.username).leftMap(_.asDetails),
        PlainPassword.validated(payload.password).leftMap(_.asDetails)
      ).parMapN((_, _))
        .fold(
          errorDetails => Login.Error.makeSemanticInputError(errorDetails).raiseError,
          _.pure[F]
        )

  object Error:
    def makeSemanticInputError(errorDetails: ErrorDetails) =
      SemanticInputError.make(ErrorCode("1000"), ErrorMessage("Invalid username or password"), errorDetails)

    def failToLogin(error: AuthenticationError): ErrorInfo =
      val errorCode = error match
        case UserNotFoundError     => ErrorCode("1001")
        case InvalidPasswordError  => ErrorCode("1002")
        case AccountNotActiveError => ErrorCode("1003")

      ErrorInfo.make(
        errorCode,
        ErrorMessage("Attempt to login failed")
      )

  object json:
    import io.circe.{Decoder, Encoder}
    import io.circe.generic.semiauto.*

    given Encoder[LoginRequestPayload] = deriveEncoder
    given Decoder[LoginRequestPayload] = deriveDecoder
    given Encoder[LoginResponsePayload] = deriveEncoder
    given Decoder[LoginResponsePayload] = deriveDecoder
