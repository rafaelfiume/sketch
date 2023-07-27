package org.fiume.sketch.storage.documents.http

import cats.MonadThrow
import cats.data.{EitherT, NonEmptyChain}
import cats.effect.{Concurrent, Sync}
import cats.effect.kernel.Async
import cats.implicits.*
import fs2.Stream
import org.fiume.sketch.shared.app.troubleshooting.{ErrorInfo, InvariantError, InvariantHolder}
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo.*
import org.fiume.sketch.shared.app.troubleshooting.http.JsonCodecs.ErrorInfoCodecs.given
import org.fiume.sketch.storage.documents.Document
import org.fiume.sketch.storage.documents.Document.Metadata
import org.fiume.sketch.storage.documents.algebras.DocumentsStore
import org.fiume.sketch.storage.documents.http.DocumentsRoutes.InvalidDocumentError.*
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

  def router(): HttpRoutes[F] = Router(prefix -> httpRoutes)

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
            value <- uploadRequest.validated()
            res <- value match
              case Left(inputErrors) =>
                // TODO Log username
                logger.info("Bad request to upload document") *>
                  BadRequest(
                    ErrorInfo.withDetails(
                      code = ErrorCode.InvalidDocument,
                      message = ErrorMessage("The provided document is invalid."),
                      details = inputErrors
                    )
                  )

              case Right(document) =>
                for
                  _ <- logger.info(s"Uploading document ${document.metadata.name}")
                  uuid <- store.commit { store.store(document) }
                  created <- Created(uuid)
                  _ <- logger.info(s"Document ${document.metadata.name} uploaded")
                yield created
          yield res
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

private[http] object DocumentsRoutes extends InvariantHolder[InvalidDocumentError]:
  trait InvalidDocumentError extends InvariantError

  object InvalidDocumentError:
    case object MissingMetadata extends InvalidDocumentError:
      def uniqueCode = "document.missing.metadata"
      def message = "metadata is mandatory"

    case object MissingContent extends InvalidDocumentError:
      def uniqueCode = "document.missing.content"
      def message = "no document provided for upload"

    case object MalformedDocumentMetadata extends InvalidDocumentError:
      def uniqueCode = "document.metadata.malformed"
      def message = "the provided document is malformed"

  // TODO Instantiate a Document, then Document is an InvariantHolder
  override val invariantErrors: Set[InvalidDocumentError] =
    Set(MissingMetadata, MissingContent, MalformedDocumentMetadata)

  extension [F[_]: MonadThrow: Concurrent](m: Multipart[F])
    def validated(): F[Either[ErrorDetails, Document[F]]] =
      // warning: errors won't accumulate by default: see validation tests
      (m.metadata(), m.bytes()).parTupled
        .map(Document.apply[F])
        .leftMap(_.toList)
        .leftMap(InvariantError.inputErrorsToMap)
        .leftMap(ErrorDetails.apply)
        .value

    private def metadata(): EitherT[F, NonEmptyChain[InvalidDocumentError], Metadata] = EitherT
      .fromEither {
        m.parts
          .find { _.name == Some("metadata") }
          .toRight(NonEmptyChain.one(MissingMetadata)) // TODO This is not the place to return MissingMetadata
      }
      .flatMap { json =>
        json
          .as[Metadata]
          .attemptT // Raise exception if the JSON is malformed?
          .leftMap(_ => NonEmptyChain.one(MalformedDocumentMetadata)) // MalformedDocumentMetadata is not an invariant error
      }

    // TODO Check bytes size
    private def bytes(): EitherT[F, NonEmptyChain[InvalidDocumentError], Stream[F, Byte]] = EitherT
      .fromEither {
        m.parts
          .find { _.name == Some("bytes") }
          .toRight(NonEmptyChain.one(MissingContent))
          .map(_.body)
      }
