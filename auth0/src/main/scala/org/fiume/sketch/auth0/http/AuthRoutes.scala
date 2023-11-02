package org.fiume.sketch.auth0.http

import cats.effect.Async
import cats.implicits.*
import org.fiume.sketch.auth0.Authenticator
import org.fiume.sketch.auth0.http.AuthRoutes.*
import org.fiume.sketch.auth0.http.AuthRoutes.Model.{LoginRequestPayload, LoginResponsePayload}
import org.fiume.sketch.auth0.http.AuthRoutes.Model.Json.given
import org.fiume.sketch.shared.app.http4s.middlewares.SemanticInputError
import org.fiume.sketch.shared.app.troubleshooting.{ErrorInfo, ErrorMessage}
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo.given
import org.fiume.sketch.shared.app.troubleshooting.InvariantErrorSyntax.asDetails
import org.fiume.sketch.shared.app.troubleshooting.http.json.ErrorInfoCodecs.given
import org.fiume.sketch.shared.auth0.Passwords.PlainPassword
import org.fiume.sketch.shared.auth0.User.Username
import org.http4s.{HttpRoutes, Response}
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router

class AuthRoutes[F[_]: Async](authenticator: Authenticator[F]) extends Http4sDsl[F]:
  private val prefix = "/"

  def router(): HttpRoutes[F] = Router(prefix -> httpRoutes)

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
                ErrorMessage("The username or password provided is incorrect.")
              )
            )
      yield resp
    }
  }

object AuthRoutes:
  object Model:
    case class LoginRequestPayload(username: String, password: String)
    case class LoginResponsePayload(token: String)

    extension (payload: LoginRequestPayload)
      def validated[F[_]: Async](): F[(Username, PlainPassword)] =
        (
          Username.validated(payload.username).leftMap(_.asDetails),
          PlainPassword.validated(payload.password).leftMap(_.asDetails),
        ).parMapN((_, _))
          .fold(
            errorDetails => SemanticInputError.makeFrom(errorDetails).raiseError,
            _.pure[F]
          )

    object Json:
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
