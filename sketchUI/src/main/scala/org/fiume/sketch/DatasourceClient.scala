package org.fiume.sketch

import cats.effect.IO
import fs2.io.file.Path
import org.http4s.{MediaType, Request, Uri}
import org.http4s.client.Client
import org.http4s.client.dsl.io.*
import org.http4s.dom.FetchClientBuilder
import org.http4s.multipart.Multiparts
import org.http4s.multipart.Part
import org.http4s.headers.`Content-Type`
import org.http4s.Method.*
import org.http4s.Uri
import java.nio.file.{Paths, Files}
import io.circe.Json
import org.http4s.circe.*

trait Storage:
  def storeDocument(payload: String, file: Path): IO[Json]

object DatasourceClient:
  def make(baseUri: Uri) = new Storage:
    // For a discussion of how to use IO and laminar
    private val client: Client[IO] = FetchClientBuilder[IO].create

    def storeDocument(payload: String, file: Path): IO[Json] =
      Multiparts.forSync[IO].flatMap {
        _.multipart(
          Vector(
            Part.formData("metadata", payload),
            Part.fileData("document", file, `Content-Type`(MediaType.image.jpeg))
          )
        ).flatMap { multipart =>
          client
            .expect[Json](POST(multipart, baseUri / "documents").withHeaders(multipart.headers))
        }
      }
