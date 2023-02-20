package org.fiume.sketch

import io.circe.Json
import org.fiume.sketch.domain.Document
import org.fiume.sketch.domain.JsonCodecs.Documents.given
import sttp.client3.*
import sttp.client3.circe.*
import scala.concurrent.ExecutionContext.Implicits.global // TODO Pass proper ec
import scala.concurrent.Future
import sttp.capabilities.WebSockets

trait Storage[F[_], A]: // TODO Rename `datastore` package to `storage`
  def store(document: Document): F[A]

object StorageHttpClient:
  def make(baseUri: String) = new Storage[Future, Document.Metadata]:
    val backend: SttpBackend[Future, WebSockets] = FetchBackend()

    def store(document: Document): Future[Document.Metadata] =
      basicRequest
        .post(uri"$baseUri/documents")
        .multipartBody(
          multipart("metadata", document.metadata),
          multipart("bytes", document.bytes)
        )
        .contentLength(document.bytes.length)
        // TODO pass origin over config
        .header("Origin", "https://localhost:5173")
        .response(asJson[Document.Metadata])
        .send(backend)
        .map(_.body)
        .flatMap {
          case Left(e)  => Future.failed(e)
          case Right(r) => Future(r)
        }
