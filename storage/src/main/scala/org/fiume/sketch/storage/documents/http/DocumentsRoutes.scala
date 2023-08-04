package org.fiume.sketch.storage.documents.http

import cats.MonadThrow
import cats.data.{EitherT, NonEmptyChain}
import cats.effect.{Concurrent, Sync}
import cats.effect.kernel.Async
import cats.implicits.*
import fs2.Stream
import org.fiume.sketch.shared.app.http4s.middlewares.{
  ErrorInfoMiddleware,
  SemanticInputError,
  SyntaxInputError,
  TraceAuditLogMiddleware
}
import org.fiume.sketch.shared.app.troubleshooting.{ErrorCode, ErrorDetails, ErrorMessage, InvariantError}
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo.given
import org.fiume.sketch.shared.app.troubleshooting.InvariantErrorSyntax.asDetails
import org.fiume.sketch.storage.documents.Document
import org.fiume.sketch.storage.documents.Document.Metadata
import org.fiume.sketch.storage.documents.Document.Metadata.*
import org.fiume.sketch.storage.documents.Document.Metadata.Name.InvalidDocumentNameError
import org.fiume.sketch.storage.documents.algebras.DocumentsStore
import org.fiume.sketch.storage.documents.http.PayloadCodecs.Document.given
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
import DocumentsRoutes.Model.MetadataPayload

/*
 * - TODO Endpoint to update documents
 * - TODO Fix warning
 * - TODO Make sure there is a limit to the size of documents that can be uploaded
 */
class DocumentsRoutes[F[_]: Async, Txn[_]](enableLogging: Boolean)(store: DocumentsStore[F, Txn]) extends Http4sDsl[F]:
  private val prefix = "/"

  def router(): HttpRoutes[F] = Router(
    prefix -> TraceAuditLogMiddleware(Slf4jLogger.getLogger[F], enableLogging)(ErrorInfoMiddleware(httpRoutes))
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
            uuid <- store.commit { store.store(document) }
            created <- Created(uuid)
          yield created
        }

      case GET -> Root / "documents" / UUIDVar(uuid) / "metadata" =>
        for
          metadata <- store.commit { store.fetchMetadata(uuid) }
          res <- metadata.fold(ifEmpty = NotFound())(Ok(_))
        yield res

      case GET -> Root / "documents" / UUIDVar(uuid) =>
        for
          stream <- store.commit { store.fetchContent(uuid) }
          res <- stream.fold(ifEmpty = NotFound())(Ok(_))
        yield res

      case DELETE -> Root / "documents" / UUIDVar(uuid) =>
        for
          metadata <- store.commit { store.fetchMetadata(uuid) }
          res <- metadata match
            case None    => NotFound()
            case Some(_) => store.commit { store.delete(uuid) } >> NoContent()
        yield res
    }

private[http] object DocumentsRoutes:

  object Model:
    case class MetadataPayload(name: String, description: String)

  extension [F[_]: MonadThrow: Concurrent](m: Multipart[F])
    def validated(): F[Document[F]] =
      (m.metadata(), m.bytes()).parTupled
        .foldF(errors => semanticInputError(errors).raiseError, _.pure[F])
        .flatMap { case (metadataPayload, bytes) =>
          metadataPayload
            .as[MetadataPayload]
            .attemptT
            .leftMap(_ =>
              ErrorDetails(Map("malformed.document.metadata.payload" -> "the metadata payload does not meet the contract"))
            )
            .foldF(errors => SyntaxInputError(errors).raiseError, (_, bytes).pure[F])
        }
        .flatMap { case (payload, stream) =>
          (
            EitherT.fromEither(Name.validated(payload.name).leftMap(_.asDetails)),
            EitherT.pure(Description(payload.description)),
            EitherT.pure(stream)
          ).parMapN((name, description, bytes) => Document(Metadata(name, description), bytes))
            .foldF(errors => semanticInputError(errors).raiseError, _.pure[F])
        }

    private def metadata(): EitherT[F, ErrorDetails, Part[F]] = EitherT
      .fromEither {
        m.parts
          .find { _.name == Some("metadata") }
          .toRight(
            ErrorDetails(
              Map("missing.document.metadata.part" -> "missing `metadata` json payload in the multipart request")
            )
          )
      }

    // TODO Check bytes size?
    private def bytes(): EitherT[F, ErrorDetails, Stream[F, Byte]] = EitherT
      .fromEither {
        m.parts
          .find { _.name == Some("bytes") }
          .toRight(
            ErrorDetails(
              Map(
                "missing.document.bytes.part" -> "missing `bytes` stream in the multipart request (please, select a file to be uploaded)"
              )
            )
          )
          .map(_.body)
      }

    private def semanticInputError(errors: ErrorDetails) =
      SemanticInputError(
        ErrorCode.InvalidDocument,
        ErrorMessage("Your document upload request is incomplete or contains invalid data."),
        errors
      )
