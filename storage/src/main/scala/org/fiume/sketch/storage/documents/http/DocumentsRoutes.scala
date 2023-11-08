package org.fiume.sketch.storage.documents.http

import cats.MonadThrow
import cats.data.EitherT
import cats.effect.Concurrent
import cats.implicits.*
import fs2.Stream
import io.circe.{Decoder, Encoder, HCursor, *}
import io.circe.{Json as JJson}
import io.circe.Decoder.Result
import io.circe.syntax.*
import org.fiume.sketch.shared.app.http4s.middlewares.SemanticInputError
import org.fiume.sketch.shared.app.troubleshooting.ErrorDetails
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo.given
import org.fiume.sketch.shared.app.troubleshooting.InvariantErrorSyntax.asDetails
import org.fiume.sketch.shared.auth0.{User, UserId}
import org.fiume.sketch.storage.documents.{Document, DocumentId, DocumentWithId, DocumentWithStream}
import org.fiume.sketch.storage.documents.Document.Metadata
import org.fiume.sketch.storage.documents.Document.Metadata.*
import org.fiume.sketch.storage.documents.algebras.DocumentsStore
import org.fiume.sketch.storage.documents.http.DocumentsRoutes.{
  AuthorQueryParamMatcher,
  DocumentIdVar,
  Line,
  Linebreak,
  NewlineDelimitedJson,
  NewlineDelimitedJsonEncoder,
  OwnerQueryParamMatcher
}
import org.fiume.sketch.storage.documents.http.DocumentsRoutes.Model.*
import org.fiume.sketch.storage.documents.http.DocumentsRoutes.Model.Json.given
import org.http4s.{Charset, EntityEncoder, HttpRoutes, MediaType, *}
import org.http4s.MediaType.application
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.Http4sDsl
import org.http4s.dsl.impl.*
import org.http4s.headers.{`Content-Disposition`, `Content-Type`}
import org.http4s.multipart.{Multipart, Part, *}
import org.http4s.server.{AuthMiddleware, Router}
import org.http4s.server.middleware.EntityLimiter

class DocumentsRoutes[F[_]: Concurrent, Txn[_]](
  authMiddleware: AuthMiddleware[F, User],
  documentBytesSizeLimit: Int,
  store: DocumentsStore[F, Txn]
) extends Http4sDsl[F]:

  private val prefix = "/"

  def router(): HttpRoutes[F] = Router(
    prefix -> EntityLimiter(authMiddleware(authedRoutes), documentBytesSizeLimit)
  )

  given EntityEncoder[F, NewlineDelimitedJson] = NewlineDelimitedJsonEncoder.make[F]

  private val authedRoutes: AuthedRoutes[User, F] =
    AuthedRoutes.of {
      case cx @ POST -> Root / "documents" as user =>
        cx.req.decode { (uploadRequest: Multipart[F]) =>
          for
            document <- uploadRequest.validated(authorId = user.uuid)
            uuid <- store.commit { store.store(document) }
            created <- Created(uuid.asResponsePayload)
          yield created
        }

      case GET -> Root / "documents" / DocumentIdVar(uuid) / "metadata" as user =>
        for
          document <- store.commit { store.fetchDocument(uuid) }
          // TODO Change response payload
          res <- document.map(_.asResponsePayload).fold(ifEmpty = NotFound())(Ok(_))
        yield res

      case GET -> Root / "documents" / DocumentIdVar(uuid) as user =>
        for
          stream <- store.commit { store.documentStream(uuid) }
          res <- stream.fold(ifEmpty = NotFound())(Ok(_, `Content-Disposition`("attachment", Map.empty)))
        yield res

      // experimental newline delimited json
      case GET -> Root / "documents" :? AuthorQueryParamMatcher(userId) as user =>
        val responseStream = store
          .fetchByAuthor(by = userId)
          .map(_.asResponsePayload.asJson)
          .map(Line(_))
          .intersperse(Linebreak)
        Ok(responseStream)

      case GET -> Root / "documents" :? OwnerQueryParamMatcher(userId) as user =>
        val responseStream = store
          .fetchByOwner(by = userId)
          .map(_.asResponsePayload.asJson)
          .map(Line(_))
          .intersperse(Linebreak)
        Ok(responseStream)

      case DELETE -> Root / "documents" / DocumentIdVar(uuid) as user =>
        for
          metadata <- store.commit { store.fetchDocument(uuid) }
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

  object DocumentIdVar:
    def unapply(value: String): Option[DocumentId] = DocumentId.fromString(value).toOption

  given QueryParamDecoder[UserId] = QueryParamDecoder[String].emap(s =>
    UserId.fromString(s).leftMap(e => ParseFailure("invalid or missing userId", s"${e.message}"))
  )

  object AuthorQueryParamMatcher extends QueryParamDecoderMatcher[UserId]("author")

  object OwnerQueryParamMatcher extends QueryParamDecoderMatcher[UserId]("owner")

  object Model:
    case class MetadataRequestPayload(name: String, description: String, ownedBy: String)
    case class MetadataResponsePayload(name: String, description: String, createdBy: String, ownedBy: String)
    case class DocumentResponsePayload(uuid: DocumentId, metadata: MetadataResponsePayload, uri: Uri)
    case class DocumentIdResponsePayload(value: DocumentId)

    extension (m: Metadata)
      def asRequestPayload: MetadataRequestPayload =
        MetadataRequestPayload(m.name.value, m.description.value, m.ownedBy.toString)

      private def asResponsePayload: MetadataResponsePayload =
        MetadataResponsePayload(m.name.value, m.description.value, m.createdBy.toString, m.ownedBy.toString)

    extension [F[_]](d: DocumentWithId)
      def asResponsePayload: DocumentResponsePayload =
        DocumentResponsePayload(d.uuid, d.metadata.asResponsePayload, Uri.unsafeFromString(s"/documents/${d.uuid.toString}"))

    extension [F[_]](id: DocumentId) def asResponsePayload: DocumentIdResponsePayload = DocumentIdResponsePayload(id)

    extension [F[_]: MonadThrow: Concurrent](m: Multipart[F])
      def validated(authorId: UserId): F[DocumentWithStream[F]] =
        (m.metadata(), m.bytes()).parTupled
          .foldF(
            details => SemanticInputError.makeFrom(details).raiseError,
            _.pure[F]
          )
          .flatMap { case (metadataPayload, bytes) =>
            metadataPayload
              .as[MetadataRequestPayload]
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
              EitherT.pure(stream),
              EitherT.fromEither(UserId.fromString(payload.ownedBy).leftMap(_.asDetails)) // Yolo
            ).parMapN((name, description, bytes, ownedBy) =>
              Document.withStream[F](bytes, Metadata(name, description, authorId, ownedBy))
            ).foldF(
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
      given Decoder[Uri] = Decoder.decodeString.emap { uri => Uri.fromString(uri).leftMap(_.getMessage) }

      given Encoder[DocumentId] = Encoder[String].contramap[DocumentId](_.toString)
      given Decoder[DocumentId] = Decoder[String].emap(uuid => DocumentId.fromString(uuid).leftMap(_.message))

      given Decoder[MetadataRequestPayload] = new Decoder[MetadataRequestPayload]:
        override def apply(c: HCursor): Result[MetadataRequestPayload] =
          for
            name <- c.downField("name").as[String]
            description <- c.downField("description").as[String]
            ownedBy <- c.downField("ownedBy").as[String]
          yield MetadataRequestPayload(name, description, ownedBy)

      // TODO Contract test
      given Encoder[MetadataResponsePayload] = new Encoder[MetadataResponsePayload]:
        override def apply(m: MetadataResponsePayload): JJson = JJson.obj(
          "name" -> m.name.asJson,
          "description" -> m.description.asJson,
          "createdBy" -> m.createdBy.asJson,
          "ownedBy" -> m.ownedBy.asJson
        )

      // TODO Contract test
      given Encoder[DocumentResponsePayload] = new Encoder[DocumentResponsePayload]:
        override def apply(d: DocumentResponsePayload): JJson = JJson.obj(
          "uuid" -> d.uuid.asJson,
          "uri" -> d.uri.asJson,
          "metadata" -> d.metadata.asJson
        )

      // TODO Contract test
      given Encoder[DocumentIdResponsePayload] = new Encoder[DocumentIdResponsePayload]:
        override def apply(uuid: DocumentIdResponsePayload): JJson = JJson.obj("uuid" -> uuid.value.asJson)
