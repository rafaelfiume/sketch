package org.fiume.sketch.shared.auth0.http

import cats.effect.Async
import cats.implicits.*
import org.fiume.sketch.shared.auth0.{JwtToken, UserId}
import org.fiume.sketch.shared.auth0.Passwords.PlainPassword
import org.fiume.sketch.shared.auth0.User.Username
import org.fiume.sketch.shared.auth0.http.Model.{LoginRequestPayload, LoginResponsePayload}
import org.fiume.sketch.shared.auth0.http.Model.json.given
import org.http4s.{Request, Uri}
import org.http4s.Method.*
import org.http4s.Status.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.client.*
import org.http4s.headers.Authorization

object HttpAuth0Client:
  def make[F[_]: Async](client: Client[F], baseUri: Uri): HttpAuth0Client[F] = new HttpAuth0Client(client, baseUri)

// TODO Refine error handling
class HttpAuth0Client[F[_]: Async] private (client: Client[F], baseUri: Uri):

  // Pending: perhaps return an AuthenticatedUser type?
  def login(username: Username, password: PlainPassword): F[Either[String, JwtToken]] =
    val request = Request[F](
      method = POST,
      uri = baseUri / "login"
    ).withEntity(LoginRequestPayload(username.value, password.value))
    // TODO retries
    client.run(request).use { response =>
      response.status match
        case Ok =>
          response.as[LoginResponsePayload].attempt.map {
            case Right(payload) => Right(JwtToken.makeUnsafeFromString(payload.token))
            case Left(_)        => Left("Failed to decode JWT")
          }
        case Unauthorized => Left("Invalid username or password").pure[F]
        case status       => Left(s"Unexpected error: $status").pure[F]
    }

  def markAccountForDeletion(id: UserId, jwt: JwtToken): F[Either[String, Unit]] =
    for
      authHeader <- Async[F].delay { Authorization.parse(s"Bearer ${jwt.value}") }
      request = Request[F](DELETE, uri = baseUri / "users" / id.value).withHeaders(authHeader)
      response <- client
        .status(request)
        .map {
          case Ok        => Right(())
          case Forbidden => Left("You are not allowed to delete this user")
          case status    => Left(s"Unexpected status code: $status")
        }
    yield response
