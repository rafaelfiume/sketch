package org.fiume.sketch.shared.auth.http

import cats.effect.Async
import cats.implicits.*
import org.fiume.sketch.shared.auth.domain.{Jwt, UserId}
import org.http4s.{Request, Uri}
import org.http4s.Method.*
import org.http4s.Status.*
import org.http4s.client.*
import org.http4s.headers.Authorization

object HttpAccountClient:
  def make[F[_]: Async](config: HttpClientConfig, client: Client[F]): HttpAccountClient[F] =
    new HttpAccountClient(config.baseUri, client)

class HttpAccountClient[F[_]: Async] private (baseUri: Uri, client: Client[F]):

  def markAccountForDeletion(id: UserId, jwt: Jwt): F[Either[String, Unit]] =
    for
      authHeader <- Async[F].delay { Authorization.parse(s"Bearer ${jwt.value}") }
      request = Request[F](DELETE, uri = baseUri / "users" / id.value).withHeaders(authHeader)
      response <- client
        .status(request)
        .map {
          case Ok        => Right(())
          case Forbidden => Left("")
          case status    => Left(s"Unexpected status code: $status")
        }
    yield response
