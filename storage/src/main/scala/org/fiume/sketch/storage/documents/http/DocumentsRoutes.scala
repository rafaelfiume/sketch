package org.fiume.sketch.storage.documents.http

import cats.MonadThrow
import cats.data.EitherT
import cats.effect.Concurrent
import cats.effect.kernel.Async
import cats.implicits.*
import fs2.Stream
import org.fiume.sketch.shared.app.http4s.middlewares.{
  SemanticInputError,
  SemanticValidationMiddleware,
  TraceAuditLogMiddleware,
  WorkerMiddleware
}
import org.fiume.sketch.shared.app.troubleshooting.ErrorDetails
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo.given
import org.fiume.sketch.shared.app.troubleshooting.InvariantErrorSyntax.asDetails
import org.fiume.sketch.storage.documents.Document
import org.fiume.sketch.storage.documents.Document.Metadata
import org.fiume.sketch.storage.documents.Document.Metadata.*
import org.fiume.sketch.storage.documents.algebras.DocumentsStore
import org.fiume.sketch.storage.documents.http.DocumentsRoutes.Json.given
import org.http4s.{HttpRoutes, *}
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.Http4sDsl
import org.http4s.multipart.{Multipart, Part, *}
import org.http4s.server.Router
import org.http4s.server.middleware.EntityLimiter
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.concurrent.ExecutionContext

import DocumentsRoutes.*
import DocumentsRoutes.Model.MetadataPayload

/*
 * TODO Update documents
 */
class DocumentsRoutes[F[_], Txn[_]](enableLogging: Boolean, documentSizeLimit: Int = 6 * 1024 * 1024 /*6Mb*/ )(
  workerPool: ExecutionContext,
  store: DocumentsStore[F, Txn]
)(using F: Async[F])
    extends Http4sDsl[F]:

  private val prefix = "/"

  def router(): HttpRoutes[F] = Router(
    prefix ->
      EntityLimiter(
        WorkerMiddleware[F](workerPool)
          .andThen(TraceAuditLogMiddleware[F](Slf4jLogger.getLogger[F], enableLogging))
          .andThen(SemanticValidationMiddleware.apply)(httpRoutes),
        limit = documentSizeLimit
      )
  )

  private val httpRoutes: HttpRoutes[F] =
    HttpRoutes.of[F] {
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
        .foldF(
          details => SemanticInputError.makeFrom(details).raiseError,
          _.pure[F]
        )
        .flatMap { case (metadataPayload, bytes) =>
          metadataPayload
            .as[MetadataPayload]
            .attemptT
            .leftMap(_ =>
              ErrorDetails(Map("malformed.document.metadata.payload" -> "the metadata payload does not meet the contract"))
            )
            .foldF(
              details => SemanticInputError.makeFrom(details).raiseError,
              (_, bytes).pure[F]
            )
        }
        .flatMap { case (payload, stream) =>
          (
            EitherT.fromEither(Name.validated(payload.name).leftMap(_.asDetails)),
            EitherT.pure(Description(payload.description)),
            EitherT.pure(stream)
          ).parMapN((name, description, bytes) => Document(Metadata(name, description), bytes))
            .foldF(
              details => SemanticInputError.makeFrom(details).raiseError,
              _.pure[F]
            )
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

  object Json:
    import io.circe.{Json}
    import io.circe.syntax.*
    import java.util.UUID
    import cats.implicits.*
    import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json as JJson}
    import io.circe.Decoder.Result
    import org.fiume.sketch.shared.app.troubleshooting.InvariantErrorSyntax.*
    import org.fiume.sketch.storage.documents.Document.Metadata
    import org.fiume.sketch.storage.documents.Document.Metadata.*
    import org.fiume.sketch.storage.documents.http.DocumentsRoutes.Model.MetadataPayload

    given Encoder[Name] = Encoder.encodeString.contramap(_.value)
    given Decoder[Name] = Decoder.decodeString.emap(Name.validated(_).leftMap(_.asString))

    given Encoder[Description] = Encoder.encodeString.contramap(_.value)
    given Decoder[Description] = Decoder.decodeString.map(Description.apply)

    given Encoder[Metadata] = new Encoder[Metadata]:
      override def apply(metadata: Metadata): JJson =
        JJson.obj(
          "name" -> metadata.name.asJson,
          "description" -> metadata.description.asJson
        )

    given Decoder[Metadata] = new Decoder[Metadata]:
      override def apply(c: HCursor): Result[Metadata] =
        for
          name <- c.downField("name").as[Metadata.Name]
          description <- c.downField("description").as[Metadata.Description]
        yield Metadata(name, description)

    given Encoder[UUID] = new Encoder[UUID]:
      override def apply(uuid: UUID): JJson = JJson.obj("uuid" -> JJson.fromString(uuid.toString))

    given Decoder[UUID] = new Decoder[UUID]:
      override def apply(c: HCursor): Result[UUID] =
        c.downField("uuid").as[String].flatMap { uuid =>
          Either.catchNonFatal(UUID.fromString(uuid)).leftMap { e => DecodingFailure(e.getMessage, c.history) }
        }

    given Decoder[MetadataPayload] = new Decoder[MetadataPayload]:
      override def apply(c: HCursor): Result[MetadataPayload] =
        for
          name <- c.downField("name").as[String]
          description <- c.downField("description").as[String]
        yield MetadataPayload(name, description)
