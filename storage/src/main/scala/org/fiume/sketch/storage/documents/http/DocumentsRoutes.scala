package org.fiume.sketch.storage.documents.http

import cats.MonadThrow
import cats.data.EitherT
import cats.effect.Concurrent
import cats.effect.kernel.Async
import cats.implicits.*
import fs2.Stream
import io.circe.{Decoder, Encoder, HCursor, *}
import io.circe.{Json as JJson}
import io.circe.Decoder.Result
import io.circe.syntax.*
import org.fiume.sketch.shared.app.http4s.middlewares.{
  SemanticInputError,
  SemanticValidationMiddleware,
  TraceAuditLogMiddleware,
  WorkerMiddleware
}
import org.fiume.sketch.shared.app.troubleshooting.ErrorDetails
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo.given
import org.fiume.sketch.shared.app.troubleshooting.InvariantErrorSyntax.asDetails
import org.fiume.sketch.shared.auth0.User
import org.fiume.sketch.storage.documents.{Document, DocumentWithUuid}
import org.fiume.sketch.storage.documents.Document.Metadata
import org.fiume.sketch.storage.documents.Document.Metadata.*
import org.fiume.sketch.storage.documents.algebras.DocumentsStore
import org.fiume.sketch.storage.documents.http.DocumentsRoutes.{
  Line,
  Linebreak,
  NewlineDelimitedJson,
  NewlineDelimitedJsonEncoder
}
import org.fiume.sketch.storage.documents.http.DocumentsRoutes.Model.*
import org.fiume.sketch.storage.documents.http.DocumentsRoutes.Model.Json.given
import org.http4s.{Charset, EntityEncoder, HttpRoutes, MediaType, *}
import org.http4s.MediaType.application
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.{`Content-Disposition`, `Content-Type`}
import org.http4s.multipart.{Multipart, Part, *}
import org.http4s.server.{AuthMiddleware, Router}
import org.http4s.server.middleware.EntityLimiter
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.util.UUID
import scala.concurrent.ExecutionContext

/*
 * TODO Update documents
 */
class DocumentsRoutes[F[_], Txn[_]](
  enableLogging: Boolean,
  workerPool: ExecutionContext,
  authMiddleware: AuthMiddleware[F, User],
  documentSizeLimit: Int = 6 * 1024 * 1024 /*6Mb*/
)(
  store: DocumentsStore[F, Txn]
)(using F: Async[F])
    extends Http4sDsl[F]:

  private val prefix = "/"

  def router(): HttpRoutes[F] = Router(
    prefix ->
      EntityLimiter(
        WorkerMiddleware[F](workerPool)
          .andThen(TraceAuditLogMiddleware[F](Slf4jLogger.getLogger[F], enableLogging))
          .andThen(SemanticValidationMiddleware.apply)(authMiddleware(authedRoutes)),
        limit = documentSizeLimit
      )
  )

  given EntityEncoder[F, NewlineDelimitedJson] = NewlineDelimitedJsonEncoder.make[F]

  private val authedRoutes: AuthedRoutes[User, F] =
    AuthedRoutes.of {
      case cx @ POST -> Root / "documents" as user =>
        cx.req.decode { (uploadRequest: Multipart[F]) =>
          for
            document <- uploadRequest.validated()
            uuid <- store.commit { store.store(document) }
            created <- Created(uuid)
          yield created
        }

      case GET -> Root / "documents" / UUIDVar(uuid) / "metadata" as user =>
        for
          metadata <- store.commit { store.fetchMetadata(uuid) }
          res <- metadata.map(_.toPayload).fold(ifEmpty = NotFound())(Ok(_))
        yield res

      case GET -> Root / "documents" / UUIDVar(uuid) as user =>
        for
          stream <- store.commit { store.fetchContent(uuid) }
          res <- stream.fold(ifEmpty = NotFound())(Ok(_, `Content-Disposition`("attachment", Map.empty)))
        yield res

      // experimental newline delimented json: no tests and subject to change
      case GET -> Root / "documents" as user =>
        val responseStream = store
          .fetchAll()
          .map(_.toPayload.asJson)
          .map(Line(_))
          .intersperse(Linebreak)
        Ok(responseStream)

      case DELETE -> Root / "documents" / UUIDVar(uuid) as user =>
        for
          metadata <- store.commit { store.fetchMetadata(uuid) }
          res <- metadata match
            case None    => NotFound()
            case Some(_) => store.commit { store.delete(uuid) } >> NoContent()
        yield res
    }

private[http] object DocumentsRoutes:
  sealed trait NewlineDelimitedJson
  case class Line(json: JJson) extends NewlineDelimitedJson
  case object Linebreak extends NewlineDelimitedJson

  object NewlineDelimitedJsonEncoder:
    def make[F[_]: cats.Functor]: EntityEncoder[F, NewlineDelimitedJson] =
      EntityEncoder.stringEncoder
        .contramap[NewlineDelimitedJson] { token =>
          token match
            case Line(value) => value.noSpaces
            case Linebreak   => "\n"
        }
        .withContentType(`Content-Type`(MediaType.application.json, Charset.`UTF-8`))

  object Model:
    case class MetadataPayload(name: String, description: String)
    case class DocumentResponsePayload(uuid: UUID, metadata: MetadataPayload, contentLink: Uri)

    extension (m: Metadata) def toPayload: MetadataPayload = MetadataPayload(m.name.value, m.description.value)

    extension [F[_]](d: DocumentWithUuid[F])
      def toPayload: DocumentResponsePayload =
        DocumentResponsePayload(d.uuid, d.metadata.toPayload, Uri.unsafeFromString(s"/documents/${d.uuid.toString}"))

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
      given Encoder[Uri] = Encoder.encodeString.contramap(_.renderString)
      given Decoder[Uri] = Decoder.decodeString.emap { uri =>
        Uri.fromString(uri).leftMap { e => e.getMessage }
      }

      given Encoder[UUID] = new Encoder[UUID]:
        override def apply(uuid: UUID): JJson = JJson.obj("uuid" -> JJson.fromString(uuid.toString))

      given Decoder[UUID] = new Decoder[UUID]:
        override def apply(c: HCursor): Result[UUID] =
          c.downField("uuid").as[String].flatMap { uuid =>
            Either.catchNonFatal(UUID.fromString(uuid)).leftMap { e => DecodingFailure(e.getMessage, c.history) }
          }

      given Encoder[MetadataPayload] = new Encoder[MetadataPayload]:
        override def apply(m: MetadataPayload): JJson = JJson.obj(
          "name" -> m.name.asJson,
          "description" -> m.description.asJson
        )

      given Decoder[MetadataPayload] = new Decoder[MetadataPayload]:
        override def apply(c: HCursor): Result[MetadataPayload] =
          for
            name <- c.downField("name").as[String]
            description <- c.downField("description").as[String]
          yield MetadataPayload(name, description)

      given Encoder[DocumentResponsePayload] = new Encoder[DocumentResponsePayload]:
        override def apply(d: DocumentResponsePayload): JJson = JJson.obj(
          "uuid" -> d.uuid.toString.asJson,
          "metadata" -> d.metadata.asJson,
          "content_link" -> d.contentLink.asJson
        )
