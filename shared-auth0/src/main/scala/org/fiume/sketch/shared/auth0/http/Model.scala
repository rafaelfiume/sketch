package org.fiume.sketch.shared.auth0.http

import cats.effect.Async
import cats.implicits.*
import org.fiume.sketch.shared.app.http4s.middlewares.SemanticInputError
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo
import org.fiume.sketch.shared.app.troubleshooting.InvariantErrorSyntax.asDetails
import org.fiume.sketch.shared.auth0.Passwords.PlainPassword
import org.fiume.sketch.shared.auth0.User.Username
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
    import io.circe.{Decoder, Encoder, HCursor, Json as JJson}
    import io.circe.syntax.*

    given Encoder[LoginRequestPayload] = new Encoder[LoginRequestPayload]:
      override def apply(loginRequest: LoginRequestPayload): JJson =
        JJson.obj(
          "username" -> loginRequest.username.asJson,
          "password" -> loginRequest.password.asJson
        )

    given Decoder[LoginRequestPayload] = new Decoder[LoginRequestPayload]:
      override def apply(c: HCursor): Decoder.Result[LoginRequestPayload] =
        for
          username <- c.downField("username").as[String]
          password <- c.downField("password").as[String]
        yield LoginRequestPayload(username, password)

    given Encoder[LoginResponsePayload] = new Encoder[LoginResponsePayload]:
      override def apply(loginResponse: LoginResponsePayload): JJson =
        JJson.obj("token" -> loginResponse.token.asJson)

    given Decoder[LoginResponsePayload] = new Decoder[LoginResponsePayload]:
      override def apply(c: HCursor): Decoder.Result[LoginResponsePayload] =
        c.downField("token").as[String].map(LoginResponsePayload.apply)
