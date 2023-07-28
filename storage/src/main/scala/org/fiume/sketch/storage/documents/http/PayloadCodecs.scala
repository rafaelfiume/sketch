package org.fiume.sketch.storage.documents.http

import cats.implicits.*
import io.circe.{Codec, Decoder, DecodingFailure, Encoder, HCursor, Json}
import io.circe.Decoder.Result
import io.circe.syntax.*
import org.fiume.sketch.storage.documents.Document.Metadata
import org.fiume.sketch.storage.documents.Document.Metadata.*

import java.util.UUID

object PayloadCodecs:
  given Encoder[Metadata.Name] = Encoder.encodeString.contramap(_.value)
  given Decoder[Metadata.Name] = Decoder.decodeString.map(Metadata.Name.apply)

  given Encoder[Metadata.Description] = Encoder.encodeString.contramap(_.value)
  given Decoder[Metadata.Description] = Decoder.decodeString.map(Metadata.Description.apply)

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
    override def apply(uuid: UUID): Json = Json.obj(
      "uuid" -> Json.fromString(uuid.toString)
    )

  given Decoder[UUID] = new Decoder[UUID]:
    override def apply(c: HCursor): Result[UUID] =
      c.downField("uuid").as[String].flatMap { uuid =>
        Either.catchNonFatal(UUID.fromString(uuid)).leftMap { e => DecodingFailure(e.getMessage, c.history) }
      }
