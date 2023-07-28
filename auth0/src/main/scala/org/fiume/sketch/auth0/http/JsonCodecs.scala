package org.fiume.sketch.auth0.http

import io.circe.{Decoder, Encoder, HCursor, Json}
import io.circe.syntax.*
import org.fiume.sketch.auth0.http.AuthRoutes.Model.{LoginRequestPayload, LoginResponsePayload}
import org.fiume.sketch.auth0.http.JsonCodecs.RequestResponsesCodecs.given
import org.fiume.sketch.shared.auth0.Passwords.PlainPassword
import org.fiume.sketch.shared.auth0.User
import org.fiume.sketch.shared.auth0.User.Username

object JsonCodecs:
  object RequestResponsesCodecs:
    given Encoder[LoginRequestPayload] = new Encoder[LoginRequestPayload]:
      override def apply(loginRequest: LoginRequestPayload): Json =
        Json.obj(
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
      override def apply(loginResponse: LoginResponsePayload): Json =
        Json.obj("token" -> loginResponse.token.asJson)

    given Decoder[LoginResponsePayload] = new Decoder[LoginResponsePayload]:
      override def apply(c: HCursor): Decoder.Result[LoginResponsePayload] =
        c.downField("token").as[String].map(LoginResponsePayload.apply)
