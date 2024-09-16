package org.fiume.sketch.shared.auth0.http

import cats.effect.Async
import cats.implicits.*
import org.fiume.sketch.shared.auth0.JwtToken
import org.fiume.sketch.shared.auth0.Passwords.PlainPassword
import org.fiume.sketch.shared.auth0.User.Username
import org.fiume.sketch.shared.auth0.http.Model.{LoginRequestPayload, LoginResponsePayload}
import org.fiume.sketch.shared.auth0.http.Model.json.given
import org.http4s.{Request, Uri}
import org.http4s.Method.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.client.*

object HttpAuth0Client:
  def make[F[_]: Async](client: Client[F], baseUri: Uri): HttpAuth0Client[F] = new HttpAuth0Client(client, baseUri)

class HttpAuth0Client[F[_]: Async] private (client: Client[F], baseUri: Uri):

  // Pending: perhaps return an AuthenticatedUser
  // TODO consider rebuilding the token error from the response payload
  def login(username: Username, password: PlainPassword): F[JwtToken] =
    val loginRequest = Request[F](
      method = POST,
      uri = baseUri / "login"
      // body = payload(username, password).asJson
    ).withEntity(LoginRequestPayload(username.value, password.value))

    // TODO retries
    client
      .expect[LoginResponsePayload](loginRequest)
      .map(payload => JwtToken.makeUnsafeFromString(payload.token))
