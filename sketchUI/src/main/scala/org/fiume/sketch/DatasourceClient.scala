package org.fiume.sketch

import cats.effect.IO
import io.circe.Json

trait Storage[F[_], A]:
  def storeDocument(payload: String, file: String): F[A]

object DatasourceClient:
  def make(baseUri: String) = new Storage[IO, Json]:
    //private val client: Client[IO] = FetchClientBuilder[IO].create

    def storeDocument(payload: String, file: String): IO[Json] = ???
      // TODO Replated http4s implementation,
      // ... which relies on fs2-io (not available on web-browsers)
      // Multiparts.forSync[IO].flatMap {
      //   _.multipart(
      //     Vector(
      //       Part.formData("metadata", payload),
      //       Part.fileData("document", Path(file), `Content-Type`(MediaType.image.jpeg))
      //     )
      //   ).flatMap { multipart =>
      //     client
      //       .expect[Json](POST(multipart, baseUri / "documents").withHeaders(multipart.headers))
      //   }
      //}

object ImpureDatasourceClient:
  import cats.effect.unsafe.implicits.global // integrationg with non pure code (frontend)
  import io.circe.syntax.*
  import scala.concurrent.ExecutionContext.Implicits.global // TODO Fix this (see ExecutionContext doc)
  import scala.concurrent.Future

  def make(baseUri: String) = new Storage[Future, String]:
    val storage = DatasourceClient.make(baseUri: String)

    def storeDocument(payload: String, file: String): Future[String] =
      storage
        .storeDocument(payload, file)
        .map(_.spaces2SortKeys)
        .unsafeToFuture()
        .recover { case err: Throwable => err.getMessage() }
