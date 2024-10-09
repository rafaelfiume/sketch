package org.fiume.sketch.http

import cats.{FlatMap, MonadThrow}
import cats.data.EitherT
import cats.effect.Concurrent
import cats.implicits.*
import fs2.Stream
import io.circe.syntax.*
import org.fiume.sketch.authorisation.{AccessControl, ContextualRole}
import org.fiume.sketch.http.DocumentsRoutes.DocumentIdVar
import org.fiume.sketch.http.DocumentsRoutes.Model.*
import org.fiume.sketch.http.DocumentsRoutes.Model.json.given
import org.fiume.sketch.shared.app.EntityId.given
import org.fiume.sketch.shared.app.algebras.Store.syntax.*
import org.fiume.sketch.shared.app.http4s.JsonCodecs.given
import org.fiume.sketch.shared.app.http4s.middlewares.SemanticInputError
import org.fiume.sketch.shared.app.troubleshooting.ErrorCode
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo.ErrorDetails
import org.fiume.sketch.shared.app.troubleshooting.InvariantErrorSyntax.asDetails
import org.fiume.sketch.shared.auth0.domain.User
import org.fiume.sketch.shared.domain.documents.{Document, DocumentId, DocumentWithId, DocumentWithStream}
import org.fiume.sketch.shared.domain.documents.Document.Metadata
import org.fiume.sketch.shared.domain.documents.Document.Metadata.*
import org.fiume.sketch.shared.domain.documents.algebras.DocumentsStore
import org.fiume.sketch.shared.http.json.{NewlineDelimitedJson, NewlineDelimitedJsonEncoder}
import org.fiume.sketch.shared.http.json.NewlineDelimitedJson.{Line, Linebreak}
import org.http4s.{EntityEncoder, HttpRoutes, *}
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`Content-Disposition`
import org.http4s.multipart.{Multipart, Part, *}
import org.http4s.server.{AuthMiddleware, Router}
import org.http4s.server.middleware.EntityLimiter

class DocumentsRoutes[F[_]: Concurrent, Txn[_]: FlatMap](
  authMiddleware: AuthMiddleware[F, User],
  documentBytesSizeLimit: Int,
  accessControl: AccessControl[F, Txn],
  store: DocumentsStore[F, Txn]
) extends Http4sDsl[F]:

  private val prefix = "/"

  // enable Store's syntax
  given DocumentsStore[F, Txn] = store

  def router(): HttpRoutes[F] = Router(
    prefix -> EntityLimiter(authMiddleware(authedRoutes), documentBytesSizeLimit)
  )

  given EntityEncoder[F, NewlineDelimitedJson] = NewlineDelimitedJsonEncoder.make[F]

  private val authedRoutes: AuthedRoutes[User, F] =
    AuthedRoutes.of {
      case cx @ POST -> Root / "documents" as user =>
        cx.req.decode { (uploadRequest: Multipart[F]) =>
          for
            document <- uploadRequest.validated()
            uuid <- accessControl
              .ensureAccess(user.uuid, ContextualRole.Owner) {
                store.store(document)
              }
              .commit()
            created <- Created(uuid.asResponsePayload)
          yield created
        }

      case GET -> Root / "documents" / DocumentIdVar(uuid) / "metadata" as user =>
        for
          document <- accessControl.attemptWithAuthorisation(user.uuid, uuid) { store.fetchDocument }.commit()
          res <- document match
            case Right(document)    => document.map(_.asResponsePayload).fold(ifEmpty = NotFound())(Ok(_))
            case Left(unauthorised) => Forbidden()
        yield res

      case GET -> Root / "documents" / DocumentIdVar(uuid) as user =>
        for res <- accessControl
            .canAccess(user.uuid, uuid)
            .commit()
            .ifM(
              ifTrue = Ok(store.documentStream(uuid).commitStream(), `Content-Disposition`("attachment", Map.empty)),
              ifFalse = Forbidden()
            )
        yield res

      case GET -> Root / "documents" as user =>
        val stream = accessControl
          .fetchAllAuthorisedEntityIds(user.uuid, "DocumentEntity") // always returns 200!
          .through(store.fetchDocuments)
          .commitStream()
          .map(_.asResponsePayload.asJson)
          .map(Line(_))
          .intersperse(Linebreak)
        // MIME type could be `application/jsonl`
        Ok(stream)

      case DELETE -> Root / "documents" / DocumentIdVar(uuid) as user =>
        for
          document <- accessControl
            .attemptWithAuthorisation(user.uuid, uuid) { store.delete }
            .flatTap { _ => accessControl.revokeContextualAccess(user.uuid, uuid) }
            .commit()
          res <- document match
            case Right(document)    => NoContent()
            case Left(unauthorised) => Forbidden()
        yield res
    }

private[http] object DocumentsRoutes:

  object DocumentIdVar:
    import org.fiume.sketch.shared.domain.documents.DocumentId.given
    def unapply(uuid: String): Option[DocumentId] = uuid.parsed().toOption

  object Model:
    case class MetadataRequestPayload(name: String, description: String)
    case class MetadataResponsePayload(name: String, description: String)
    case class DocumentResponsePayload(uuid: DocumentId, metadata: MetadataResponsePayload, byteStreamUri: Uri)
    case class DocumentIdResponsePayload(uuid: DocumentId)

    extension (m: Metadata)
      private def asResponsePayload: MetadataResponsePayload =
        MetadataResponsePayload(m.name.value, m.description.value)

    extension [F[_]](d: DocumentWithId)
      def asResponsePayload: DocumentResponsePayload =
        DocumentResponsePayload(d.uuid, d.metadata.asResponsePayload, Uri.unsafeFromString(s"/documents/${d.uuid.asString()}"))

    extension [F[_]](id: DocumentId) def asResponsePayload: DocumentIdResponsePayload = DocumentIdResponsePayload(id)

    private val errorCode = ErrorCode("9011")
    extension [F[_]: MonadThrow: Concurrent](m: Multipart[F])
      def validated(): F[DocumentWithStream[F]] =
        (m.metadata(), m.bytes()).parTupled
          .foldF(
            details => SemanticInputError.make(errorCode, details).raiseError,
            _.pure[F]
          )
          .flatMap { case (metadataPayload, bytes) =>
            metadataPayload
              .as[MetadataRequestPayload]
              .attemptT
              .leftMap(_ =>
                ErrorDetails("malformed.document.metadata.payload" -> "the metadata payload does not meet the contract")
              )
              .foldF(
                details => SemanticInputError.make(errorCode, details).raiseError,
                (_, bytes).pure[F]
              )
          }
          .flatMap { case (payload, stream) =>
            (
              EitherT.fromEither(Name.validated(payload.name).leftMap(_.asDetails)),
              EitherT.pure(Description(payload.description)),
              EitherT.pure(stream)
            ).parMapN((name, description, bytes) => Document.make[F](bytes, Metadata(name, description)))
              .foldF(
                details => SemanticInputError.make(errorCode, details).raiseError,
                _.pure[F]
              )
          }

      private def metadata(): EitherT[F, ErrorDetails, Part[F]] = EitherT
        .fromEither {
          m.parts
            .find { _.name == Some("metadata") }
            .toRight(
              ErrorDetails(
                "missing.document.metadata.part" -> "missing `metadata` json payload in the multipart request"
              )
            )
        }

      private def bytes(): EitherT[F, ErrorDetails, Stream[F, Byte]] = EitherT
        .fromEither {
          m.parts
            .find { _.name == Some("bytes") }
            .toRight(
              ErrorDetails(
                "missing.document.bytes.part" -> "missing `bytes` stream in the multipart request (please, select a file to be uploaded)"
              )
            )
            .map(_.body)
        }

    object json:
      import io.circe.{Decoder, Encoder, Json, *}
      import io.circe.generic.semiauto.*
      import org.http4s.circe.*

      given Decoder[DocumentId] = Decoder.decodeUUID.map(DocumentId(_))
      given Encoder[MetadataRequestPayload] = deriveEncoder
      given Decoder[MetadataRequestPayload] = deriveDecoder
      given Encoder[MetadataResponsePayload] = deriveEncoder
      given Decoder[MetadataResponsePayload] = deriveDecoder
      given Encoder[DocumentResponsePayload] = deriveEncoder
      given Decoder[DocumentResponsePayload] = deriveDecoder

      given Encoder[DocumentIdResponsePayload] = new Encoder[DocumentIdResponsePayload]:
        override def apply(uuid: DocumentIdResponsePayload): Json = Json.obj("uuid" -> uuid.uuid.asJson)

      given Decoder[DocumentIdResponsePayload] = deriveDecoder
