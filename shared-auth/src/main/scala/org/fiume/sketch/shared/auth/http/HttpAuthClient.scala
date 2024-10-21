package org.fiume.sketch.shared.auth.http

import cats.effect.Async
import cats.implicits.*
import org.fiume.sketch.shared.auth.domain.{AuthenticationError, Jwt}
import org.fiume.sketch.shared.auth.domain.AuthenticationError.*
import org.fiume.sketch.shared.auth.domain.Passwords.PlainPassword
import org.fiume.sketch.shared.auth.domain.User.Username
import org.fiume.sketch.shared.auth.http.model.Login.{LoginRequestPayload, LoginResponsePayload}
import org.fiume.sketch.shared.auth.http.model.Login.json.given
import org.fiume.sketch.shared.common.troubleshooting.ErrorInfo
import org.fiume.sketch.shared.common.troubleshooting.ErrorInfo.json.given
import org.http4s.{Request, Uri}
import org.http4s.Method.*
import org.http4s.Status.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.client.*

object HttpAuthClient:
  def make[F[_]: Async](config: HttpClientConfig, client: Client[F]): HttpAuthClient[F] =
    new HttpAuthClient(config.baseUri, client)

class HttpAuthClient[F[_]: Async] private (baseUri: Uri, client: Client[F]):

  def login(username: Username, password: PlainPassword): F[Either[AuthenticationError, Jwt]] =
    val request = Request[F](
      method = POST,
      uri = baseUri / "login"
    ).withEntity(LoginRequestPayload(username.value, password.value))
    client.run(request).use { response =>
      response.status match
        case Ok =>
          response.as[LoginResponsePayload].map(payload => Jwt.makeUnsafeFromString(payload.token).asRight)
        case Unauthorized =>
          response.as[ErrorInfo].map { error =>
            error.code.value match
              case "1001" => UserNotFoundError.asLeft
              case "1002" => InvalidPasswordError.asLeft
              case "1003" => AccountNotActiveError.asLeft
              case code   => throw new AssertionError(s"Unexpected error code: $code")
          }
        // This is a bit of an experimental design decision where:
        // * The API returns the same AuthenticatorError used on the server-side in order to avoid yet another (view-like) error ADT
        // * Unexpected results - like status code 5xx - cause an exception to be raised.
        // In theory, this should provide clear and separated semantics for expected and unexpected errors
        // at the cost of potentially lacking consistence on how error are handled
        // (one might first think that the left side of the `Either` will provide _all_ possible errors that can happen).
        case status => new RuntimeException(s"Unexpected error: $status").raiseError
    }