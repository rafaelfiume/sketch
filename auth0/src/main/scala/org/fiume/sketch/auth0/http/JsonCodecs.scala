package org.fiume.sketch.auth0.http

import io.circe.{Decoder, Encoder, HCursor, Json}
import io.circe.syntax.*
import org.fiume.sketch.auth0.http.AuthRoutes.Model.{LoginRequest, LoginResponse}
import org.fiume.sketch.auth0.http.JsonCodecs.RequestResponsesCodecs.given
import org.fiume.sketch.shared.auth0.Passwords.PlainPassword
import org.fiume.sketch.shared.auth0.User
import org.fiume.sketch.shared.auth0.User.Username

object JsonCodecs:
  object RequestResponsesCodecs:
    given Encoder[LoginRequest] = new Encoder[LoginRequest]:
      override def apply(loginRequest: LoginRequest): Json =
        Json.obj(
          "username" -> loginRequest.username.asJson,
          "password" -> loginRequest.password.asJson
        )

    given Decoder[LoginRequest] = new Decoder[LoginRequest]:
      override def apply(c: HCursor): Decoder.Result[LoginRequest] =
        for
          username <- c.downField("username").as[String]
          password <- c.downField("password").as[String]
        yield LoginRequest(username, password)

    given Encoder[LoginResponse] = new Encoder[LoginResponse]:
      override def apply(loginResponse: LoginResponse): Json =
        Json.obj("token" -> loginResponse.token.asJson)

    given Decoder[LoginResponse] = new Decoder[LoginResponse]:
      override def apply(c: HCursor): Decoder.Result[LoginResponse] =
        c.downField("token").as[String].map(LoginResponse.apply)
