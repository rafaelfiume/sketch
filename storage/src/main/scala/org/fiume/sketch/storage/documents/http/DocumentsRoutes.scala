package org.fiume.sketch.storage.documents.http

import cats.MonadThrow
import cats.data.{EitherT, NonEmptyChain}
import cats.effect.{Concurrent, Sync}
import cats.effect.kernel.Async
import cats.implicits.*
import fs2.Stream
import org.fiume.sketch.shared.app.http4s.middlewares.ErrorInfoMiddleware
import org.fiume.sketch.storage.documents.Document
import org.fiume.sketch.storage.documents.Document.Metadata
import org.fiume.sketch.storage.documents.algebras.DocumentsStore
import org.fiume.sketch.storage.documents.http.JsonCodecs.given
import org.http4s.{HttpRoutes, QueryParamDecoder, *}
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.Http4sDsl
import org.http4s.dsl.io.QueryParamDecoderMatcher
import org.http4s.multipart.{Multipart, Part, *}
import org.http4s.server.Router
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.util.UUID

import DocumentsRoutes.*

/*
 * - TODO Endpoint to update documents
 * - TODO Fix warning
 * - TODO Improve validation, for instance validate document name is not empty, has minimum length, etc.
 * - TODO Make sure there is a limit to the size of documents that can be uploaded
 */
class DocumentsRoutes[F[_]: Async, Txn[_]](store: DocumentsStore[F, Txn]) extends Http4sDsl[F]:
  private val logger = Slf4jLogger.getLogger[F]

  private val prefix = "/"

  def router(): HttpRoutes[F] = Router(
    prefix -> ErrorInfoMiddleware(httpRoutes)
  )

  private val httpRoutes: HttpRoutes[F] =
    HttpRoutes.of[F] {
      /*
       * > [io-compute-9] INFO org.fiume.sketch.storage.http.DocumentsRoutes - Received request to upload document Name(altamura.jpg)
       * > [WARNING] Your app's responsiveness to a new asynchronous event (such as a
       * > new connection, an upstream response, or a timer) was in excess of 100 milliseconds.
       * > Your CPU is probably starving. Consider increasing the granularity
       * > of your delays or adding more cedes. This may also be a sign that you are
       * > unintentionally running blocking I/O operations (such as File or InetAddress)
       * > without the blocking combinator.
       */
      case req @ POST -> Root / "documents" =>
        req.decode { (uploadRequest: Multipart[F]) =>
          for
            document <- uploadRequest.validated()
            _ <- logger.info(s"Uploading document ${document.metadata.name}")
            uuid <- store.commit { store.store(document) }
            created <- Created(uuid)
            _ <- logger.info(s"Document ${document.metadata.name} uploaded")
          yield created
        }

      case GET -> Root / "documents" / UUIDVar(uuid) / "metadata" =>
        for
          _ <- logger.info(s"Fetching document metadata of $uuid")
          result <- store.commit { store.fetchMetadata(uuid) }
          res <- result match
            case None           => NotFound()
            case Some(metadata) => Ok(metadata)
        yield res

      case GET -> Root / "documents" / UUIDVar(uuid) / "content" =>
        for
          _ <- logger.info(s"fetching content of document $uuid")
          result <- store.commit { store.fetchContent(uuid) }
          res <- result match
            case None         => NotFound()
            case Some(stream) => Ok(stream)
        yield res

      case DELETE -> Root / "documents" / UUIDVar(uuid) =>
        for
          _ <- logger.info(s"Deleting document $uuid")
          metadata <- store.commit { store.fetchMetadata(uuid) }
          res <- metadata match
            case None => NotFound()
            case Some(_) =>
              store.commit { store.delete(uuid) } >>
                NoContent()
        yield res
    }

private[http] object DocumentsRoutes:

  extension [F[_]: MonadThrow: Concurrent](m: Multipart[F])
    def validated(): F[Document[F]] =
      // warning: errors won't accumulate by default: see validation tests
      (m.metadata(), m.bytes()).parTupled
        .map(Document.apply[F])
        .foldF(
          // will be intercepted by ErrorInfoMiddleware
          errors => MalformedMessageBodyFailure(errors).raiseError[F, Document[F]],
          _.pure[F]
        )

    private def metadata(): EitherT[F, String, Metadata] = EitherT
      .fromEither {
        m.parts
          .find { _.name == Some("metadata") }
          .toRight("|document metadata is mandatory|")
      }
      .flatMap { json =>
        json
          .as[Metadata]
          .attemptT
          .leftMap(_ => "malformed json document metadata|")
      }

    // TODO Check bytes size
    private def bytes(): EitherT[F, String, Stream[F, Byte]] = EitherT
      .fromEither {
        m.parts
          .find { _.name == Some("bytes") }
          .toRight("|document content is mandatory|")
          .map(_.body)
      }
