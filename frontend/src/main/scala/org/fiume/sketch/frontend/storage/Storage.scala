package org.fiume.sketch.frontend.storage

import io.circe.Json
import org.fiume.sketch.frontend.storage.Storage
import org.fiume.sketch.shared.codecs.json.domain.Documents.given
import org.fiume.sketch.shared.domain.documents.{Document, Metadata}
import sttp.capabilities.WebSockets
import sttp.client3.*
import sttp.client3.circe.*

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait Storage[F[_], A]:
  def store(document: Document): F[A]

object StorageHttpClient:
  def make(baseUri: String) = new Storage[Future, Metadata]:
    val backend: SttpBackend[Future, WebSockets] = FetchBackend()

    def store(document: Document): Future[Metadata] =
      basicRequest
        .post(uri"$baseUri/documents")
        .multipartBody(
          multipart("metadata", document.metadata),
          multipart("bytes", document.bytes)
        )
        .contentLength(document.bytes.length)
        // TODO pass origin over config
        .header("Origin", "https://localhost:5173")
        .response(asJson[Metadata])
        .send(backend)
        .map(_.body)
        .flatMap {
          case Left(e)  => Future.failed(e)
          case Right(r) => Future(r)
        }
