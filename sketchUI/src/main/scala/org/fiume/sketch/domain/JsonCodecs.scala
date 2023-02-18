package org.fiume.sketch.domain

import io.circe.{Decoder, Encoder, HCursor, Json}
import io.circe.Decoder.Result
import io.circe.syntax.*

// TODO All copied from services for now
object JsonCodecs:
  object Documents:
    given Encoder[Document.Metadata.Name] =
      Encoder.encodeString.contramap(_.value)

    given Encoder[Document.Metadata.Description] =
      Encoder.encodeString.contramap(_.value)

    given Encoder[Document.Metadata] = new Encoder[Document.Metadata]:
      override def apply(metadata: Document.Metadata): Json =
        Json.obj(
          "name" -> metadata.name.asJson,
          "description" -> metadata.description.asJson
        )

    given Decoder[Document.Metadata.Name] =
      Decoder.decodeString.map(Document.Metadata.Name.apply)

    given Decoder[Document.Metadata.Description] =
      Decoder.decodeString.map(Document.Metadata.Description.apply)

    given Decoder[Document.Metadata] = new Decoder[Document.Metadata]:
      override def apply(c: HCursor): Result[Document.Metadata] =
        for
          name <- c.downField("name").as[Document.Metadata.Name]
          description <- c.downField("description").as[Document.Metadata.Description]
        yield Document.Metadata(name, description)
