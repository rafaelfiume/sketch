package org.fiume.sketch.shared.auth0.http

import cats.effect.Async
import cats.implicits.*
import org.fiume.sketch.shared.app.http4s.middlewares.SemanticInputError
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo
import org.fiume.sketch.shared.app.troubleshooting.InvariantErrorSyntax.asDetails
import org.fiume.sketch.shared.auth0.domain.Passwords.PlainPassword
import org.fiume.sketch.shared.auth0.domain.User.Username
import org.http4s.circe.CirceEntityEncoder.*

object Model:
  case class LoginRequestPayload(username: String, password: String)
  case class LoginResponsePayload(token: String)

  extension (payload: LoginRequestPayload)
    def validated[F[_]: Async](): F[(Username, PlainPassword)] =
      (
        Username.validated(payload.username).leftMap(_.asDetails),
        PlainPassword.validated(payload.password).leftMap(_.asDetails)
      ).parMapN((_, _))
        .fold(
          errorDetails => SemanticInputError.make(errorDetails).raiseError,
          _.pure[F]
        )

  object json:
    import io.circe.{Decoder, Encoder}
    import io.circe.generic.semiauto.*

    given Encoder[LoginRequestPayload] = deriveEncoder
    given Decoder[LoginRequestPayload] = deriveDecoder /// ?????
    given Encoder[LoginResponsePayload] = deriveEncoder
    given Decoder[LoginResponsePayload] = deriveDecoder /// ?????
