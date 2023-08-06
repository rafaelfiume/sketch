package org.fiume.sketch.acceptance.testkit

import cats.effect.IO
import munit.Assertions.*
import munit.CatsEffectSuite
import org.http4s.{MediaType, Request, Uri}
import org.http4s.Method.*
import org.http4s.client.*
import org.http4s.client.dsl.io.*
import org.http4s.client.middleware.{Retry, RetryPolicy}
import org.http4s.ember.client.*
import org.http4s.headers.`Content-Type`
import org.http4s.multipart.{Boundary, Multipart, Part}

import scala.concurrent.duration.*

trait Http4sClientContext:
  def http(exec: Client[IO] => IO[Unit]): IO[Unit] = EmberClientBuilder
    .default[IO]
    .build
    .map { retry(_) }
    .use { exec(_) }

  // TODO Make this more generic or move it from here
  def fileUploadRequest(payload: String, pathToFile: String): Request[IO] =
    val imageFile = getClass.getClassLoader.getResource(pathToFile)
    val multipart = Multipart[IO](
      parts = Vector(
        Part.formData("metadata", payload),
        Part.fileData("bytes", imageFile, `Content-Type`(MediaType.image.jpeg))
      ),
      boundary = Boundary("boundary")
    )
    "http://localhost:8080/documents".post.withEntity(multipart).withHeaders(multipart.headers)

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
