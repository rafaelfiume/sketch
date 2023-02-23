package org.fiume.sketch.domain.documents

import io.circe.{Decoder, Encoder, HCursor, Json}
import io.circe.Decoder.Result
import io.circe.syntax.*
import org.fiume.sketch.domain.documents.Metadata

object JsonCodecs:

  given Encoder[Metadata.Name] =
    Encoder.encodeString.contramap(_.value)

  given Decoder[Metadata.Name] =
    Decoder.decodeString.map(Metadata.Name.apply)

  given Encoder[Metadata.Description] =
    Encoder.encodeString.contramap(_.value)

  given Decoder[Metadata.Description] =
    Decoder.decodeString.map(Metadata.Description.apply)

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
