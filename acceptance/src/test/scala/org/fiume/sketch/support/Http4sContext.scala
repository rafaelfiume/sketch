package org.fiume.sketch.support

import cats.effect.IO
import munit.CatsEffectSuite
import munit.Assertions.*
import org.http4s.{MediaType, Request, Uri}
import org.http4s.Method.*
import org.http4s.client.*
import org.http4s.client.dsl.io.*
import org.http4s.ember.client.*
import org.http4s.headers.`Content-Type`
import org.http4s.multipart.{Boundary, Multipart, Part}

trait Http4sContext:
  def http(exec: Client[IO] => IO[Unit]): IO[Unit] = EmberClientBuilder.default[IO].build.use { exec(_) }

  def fileUploadRequest(payload: String, pathToFile: String): Request[IO] =
    val imageFile = getClass.getClassLoader.getResource(pathToFile)
    val multipart = Multipart[IO](
      parts = Vector(
        Part.formData("metadata", payload),
        Part.fileData("document", imageFile, `Content-Type`(MediaType.image.jpeg))
      ),
      boundary = Boundary("boundary")
    )
    "http://localhost:8080/documents".post.withEntity(multipart).withHeaders(multipart.headers)

  // TODO duplicated from FileContentContext
  import cats.effect.Async
  import fs2.io.file.{Files, Path}
  def bytesFrom[F[_]](path: String)(using F: Async[F]): fs2.Stream[F, Byte] =
    Files[F].readAll(Path(getClass.getClassLoader.getResource(path).getPath()))

  extension (s: String)
    def get: Request[IO] = GET(s.toUri)
    def post: Request[IO] = POST(s.toUri)
    def delete: Request[IO] = DELETE(s.toUri)
    private def toUri: Uri = Uri.unsafeFromString(s)
