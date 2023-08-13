package org.fiume.sketch.acceptance.testkit

import cats.effect.IO
import org.http4s.{Request, Uri}
import org.http4s.Method.*
import org.http4s.client.*
import org.http4s.client.dsl.io.*
import org.http4s.client.middleware.{Retry, RetryPolicy}
import org.http4s.ember.client.*

import scala.concurrent.duration.*

trait Http4sClientContext:
  def withHttp[A](exec: Client[IO] => IO[A]): IO[A] = EmberClientBuilder
    .default[IO]
    .build
    .map { retry(_) }
    .use { exec(_) }

  private def retry = Retry[IO](
    policy = RetryPolicy(
      RetryPolicy.exponentialBackoff(maxWait = 2.second, maxRetry = 30)
    )
  )

  extension (s: String)
    def get: Request[IO] = GET(s.toUri)
    def post: Request[IO] = POST(s.toUri)
    def delete: Request[IO] = DELETE(s.toUri)
    private def toUri: Uri = Uri.unsafeFromString(s)
