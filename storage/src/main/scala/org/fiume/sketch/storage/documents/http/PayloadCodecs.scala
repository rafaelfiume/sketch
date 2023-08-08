package org.fiume.sketch.storage.documents.http

import cats.implicits.*
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json}
import io.circe.Decoder.Result
import io.circe.syntax.*
import org.fiume.sketch.shared.app.troubleshooting.InvariantErrorSyntax.*
import org.fiume.sketch.storage.documents.Document.Metadata
import org.fiume.sketch.storage.documents.Document.Metadata.*
import org.fiume.sketch.storage.documents.http.DocumentsRoutes.Model.MetadataPayload

import java.util.UUID

object PayloadCodecs:
  object Document:
    given Encoder[Name] = Encoder.encodeString.contramap(_.value)
    given Decoder[Name] = Decoder.decodeString.emap(Name.validated(_).leftMap(_.asString))

    given Encoder[Description] = Encoder.encodeString.contramap(_.value)
    given Decoder[Description] = Decoder.decodeString.map(Description.apply)

    given Encoder[Metadata] = new Encoder[Metadata]:
      override def apply(metadata: Metadata): Json =
        Json.obj(
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
      override def apply(uuid: UUID): Json = Json.obj("uuid" -> Json.fromString(uuid.toString))

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
