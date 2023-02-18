package org.fiume.sketch

import io.circe.Json
import org.fiume.sketch.domain.Document
import org.fiume.sketch.domain.JsonCodecs.Documents.given
import sttp.client3.*
import sttp.client3.circe.*
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import sttp.capabilities.WebSockets

trait Storage[F[_], A]:
  def storeDocument(metadata: Document.Metadata, file: String): F[A]

object DatasourceClient:
  def make(baseUri: String) = new Storage[Future, Document.Metadata]:
    val backend: SttpBackend[Future, WebSockets] = FetchBackend()

    def storeDocument(metadata: Document.Metadata, file: String): Future[Document.Metadata] =
      val data = Array[Byte](1, 2, 3, 4, 5)
      val request =
        basicRequest
          .post(uri"$baseUri/documents")
          .multipartBody(
            multipart("metadata", metadata),
            multipart("file", data)
          )
          .response(asJson[Document.Metadata])
      request.send(backend).map(_.body).flatMap {
        case Left(e)  => Future.failed(e)
        case Right(r) => Future(r)
      }
