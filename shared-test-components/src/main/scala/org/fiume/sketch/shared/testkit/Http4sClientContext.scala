package org.fiume.sketch.shared.testkit

import cats.effect.{IO, Resource}
import org.http4s.{Request, Uri}
import org.http4s.Method.*
import org.http4s.client.*
import org.http4s.client.dsl.io.*
import org.http4s.client.middleware.{Retry, RetryPolicy}
import org.http4s.ember.client.*
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

import scala.concurrent.duration.*

trait Http4sClientContext:

  def withHttp[A](exec: Client[IO] => IO[A]): IO[A] =
    given LoggerFactory[IO] = Slf4jFactory.create[IO]
    def retry = Retry[IO](
      policy = RetryPolicy(
        RetryPolicy.exponentialBackoff(maxWait = 2.second, maxRetry = 30)
      )
    )
    makeHttpClient()
      .map { retry(_) }
      .use { exec(_) }

  def makeHttpClient(): Resource[IO, Client[IO]] =
    given LoggerFactory[IO] = Slf4jFactory.create[IO]
    EmberClientBuilder.default[IO].build

  extension (s: String)
    def get: Request[IO] = GET(s.toUri)
    def post: Request[IO] = POST(s.toUri)
    def delete: Request[IO] = DELETE(s.toUri)
    private def toUri: Uri = Uri.unsafeFromString(s)