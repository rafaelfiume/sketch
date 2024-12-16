package org.fiume.sketch.shared.auth.http.model

import cats.effect.Async
import cats.implicits.*
import org.fiume.sketch.shared.auth.AuthenticationError
import org.fiume.sketch.shared.auth.AuthenticationError.*
import org.fiume.sketch.shared.auth.Passwords.PlainPassword
import org.fiume.sketch.shared.auth.User.Username
import org.fiume.sketch.shared.common.http.middlewares.SemanticInputError
import org.fiume.sketch.shared.common.troubleshooting.ErrorInfo
import org.fiume.sketch.shared.common.troubleshooting.ErrorInfo.ErrorDetails
import org.fiume.sketch.shared.common.troubleshooting.syntax.ErrorInfoSyntax.*
import org.fiume.sketch.shared.common.troubleshooting.syntax.InvariantErrorSyntax.asDetails

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
    private[model] def makeSemanticInputError(errorDetails: ErrorDetails) =
      SemanticInputError.make("1000".code, "Invalid username or password".message, errorDetails)

    extension (error: AuthenticationError)
      def toErrorInfo =
        val errorCode = error match
          case UserNotFoundError     => "1001".code
          case InvalidPasswordError  => "1002".code
          case AccountNotActiveError => "1003".code
        ErrorInfo.make(errorCode, "Attempt to login failed".message)

  object json:
    import io.circe.{Decoder, Encoder}
    import io.circe.generic.semiauto.*

    given Encoder[LoginRequestPayload] = deriveEncoder
    given Decoder[LoginRequestPayload] = deriveDecoder
    given Encoder[LoginResponsePayload] = deriveEncoder
    given Decoder[LoginResponsePayload] = deriveDecoder
