package org.fiume.sketch.http

import cats.MonadThrow
import cats.data.EitherT
import cats.effect.Concurrent
import cats.implicits.*
import fs2.Stream
import io.circe.{Decoder, Encoder, HCursor, *}
import io.circe.Decoder.Result
import io.circe.Json as JJson
import io.circe.syntax.*
import org.fiume.sketch.http.DocumentsRoutes.{DocumentIdVar, Line, Linebreak, NewlineDelimitedJson, NewlineDelimitedJsonEncoder}
import org.fiume.sketch.http.DocumentsRoutes.Model.*
import org.fiume.sketch.http.DocumentsRoutes.Model.json.given
import org.fiume.sketch.shared.app.EntityId.given
import org.fiume.sketch.shared.app.algebras.Store.Syntax.commit
import org.fiume.sketch.shared.app.http4s.middlewares.SemanticInputError
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo.ErrorDetails
import org.fiume.sketch.shared.app.troubleshooting.InvariantErrorSyntax.asDetails
import org.fiume.sketch.shared.auth0.{User, UserId}
import org.fiume.sketch.shared.domain.documents.{Document, DocumentId, DocumentWithId, DocumentWithStream}
import org.fiume.sketch.shared.domain.documents.Document.Metadata
import org.fiume.sketch.shared.domain.documents.Document.Metadata.*
import org.fiume.sketch.shared.domain.documents.algebras.DocumentsStore
import org.http4s.{Charset, EntityEncoder, HttpRoutes, MediaType, *}
import org.http4s.MediaType.application
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.Http4sDsl
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

  given DocumentsStore[F, Txn] = store

  def router(): HttpRoutes[F] = Router(
    prefix -> EntityLimiter(authMiddleware(authedRoutes), documentBytesSizeLimit)
  )

  given EntityEncoder[F, NewlineDelimitedJson] = NewlineDelimitedJsonEncoder.make[F]

  // TODO Check if user can create resouces (`Editor`) or if she can only view (`Viewer`) it
  private val authedRoutes: AuthedRoutes[User, F] =
    AuthedRoutes.of {
      case cx @ POST -> Root / "documents" as user =>
        cx.req.decode { (uploadRequest: Multipart[F]) =>
          for
            document <- uploadRequest.validated()
            uuid <- store.store(document).commit()
            created <- Created(uuid.asResponsePayload)
          yield created
        }

      case GET -> Root / "documents" / DocumentIdVar(uuid) / "metadata" as user =>
        for
          document <- store.fetchDocument(uuid).commit()
          res <- document.map(_.asResponsePayload).fold(ifEmpty = NotFound())(Ok(_))
        yield res

      case GET -> Root / "documents" / DocumentIdVar(uuid) as user =>
        for
          stream <- store.documentStream(uuid).commit()
          res <- stream.fold(ifEmpty = NotFound())(Ok(_, `Content-Disposition`("attachment", Map.empty)))
        yield res

      // experimental newline delimited json
      case GET -> Root / "documents" as user =>
        val responseStream = store
          .fetchByOwner(by = user.uuid)
          .map(_.asResponsePayload.asJson)
          .map(Line(_))
          .intersperse(Linebreak)
        Ok(responseStream)

      case DELETE -> Root / "documents" / DocumentIdVar(uuid) as user =>
        for
          metadata <- store.fetchDocument(uuid).commit()
          res <- metadata match
            case None    => NotFound()
            case Some(_) => store.delete(uuid).commit() >> NoContent()
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
    def unapply(uuid: String): Option[DocumentId] = uuid.parsed().toOption

  object Model:
    case class MetadataRequestPayload(name: String, description: String, owner: String)
    case class MetadataResponsePayload(name: String, description: String, owner: String)
    case class DocumentResponsePayload(uuid: DocumentId, metadata: MetadataResponsePayload, byteStreamUri: Uri)
    case class DocumentIdResponsePayload(value: DocumentId)

    extension (m: Metadata)
      private def asResponsePayload: MetadataResponsePayload =
        MetadataResponsePayload(m.name.value, m.description.value, m.owner.asString())

    extension [F[_]](d: DocumentWithId)
      def asResponsePayload: DocumentResponsePayload =
        DocumentResponsePayload(d.uuid, d.metadata.asResponsePayload, Uri.unsafeFromString(s"/documents/${d.uuid.asString()}"))

    extension [F[_]](id: DocumentId) def asResponsePayload: DocumentIdResponsePayload = DocumentIdResponsePayload(id)

    extension [F[_]: MonadThrow: Concurrent](m: Multipart[F])
      def validated(): F[DocumentWithStream[F]] =
        (m.metadata(), m.bytes()).parTupled
          .foldF(
            details => SemanticInputError.make(details).raiseError,
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
                details => SemanticInputError.make(details).raiseError,
                (_, bytes).pure[F]
              )
          }
          .flatMap { case (payload, stream) =>
            (
              EitherT.fromEither(Name.validated(payload.name).leftMap(_.asDetails)),
              EitherT.pure(Description(payload.description)),
              EitherT.pure(stream),
              EitherT.fromEither(payload.owner.parsed().leftMap(_.asDetails))
            ).parMapN((name, description, bytes, ownerId: UserId) =>
              Document.make[F](bytes, Metadata(name, description, ownerId))
            ).foldF(
              details => SemanticInputError.make(details).raiseError,
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
      given Encoder[Uri] = Encoder.encodeString.contramap(_.renderString)
      given Decoder[Uri] = Decoder.decodeString.emap { uri => Uri.fromString(uri).leftMap(_.getMessage) }

      // TODO Move it to a common package?
      import org.fiume.sketch.shared.app.EntityId
      import org.fiume.sketch.shared.app.Entity
      given [T <: Entity]: Encoder[EntityId[T]] = Encoder[String].contramap[EntityId[T]](_.asString())
      given [T <: Entity]: Decoder[EntityId[T]] = Decoder[String].emap(_.parsed().leftMap(_.message))

      given Decoder[MetadataRequestPayload] = new Decoder[MetadataRequestPayload]:
        override def apply(c: HCursor): Result[MetadataRequestPayload] =
          for
            name <- c.downField("name").as[String]
            description <- c.downField("description").as[String]
            owner <- c.downField("owner").as[String]
          yield MetadataRequestPayload(name, description, owner)

      given Encoder[MetadataResponsePayload] = new Encoder[MetadataResponsePayload]:
        override def apply(m: MetadataResponsePayload): JJson = JJson.obj(
          "name" -> m.name.asJson,
          "description" -> m.description.asJson,
          "owner" -> m.owner.asJson
        )

      given Encoder[DocumentResponsePayload] = new Encoder[DocumentResponsePayload]:
        override def apply(d: DocumentResponsePayload): JJson = JJson.obj(
          "uuid" -> d.uuid.asJson,
          "byteStreamUri" -> d.byteStreamUri.asJson,
          "metadata" -> d.metadata.asJson
        )

      given Encoder[DocumentIdResponsePayload] = new Encoder[DocumentIdResponsePayload]:
        override def apply(uuid: DocumentIdResponsePayload): JJson = JJson.obj("uuid" -> uuid.value.asJson)
