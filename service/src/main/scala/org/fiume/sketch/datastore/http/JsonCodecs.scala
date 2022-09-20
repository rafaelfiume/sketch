package org.fiume.sketch.datastore.http

import cats.data.NonEmptyChain
import cats.implicits.*
import io.circe.{Decoder, Encoder, HCursor, Json}
import io.circe.Decoder.Result
import io.circe.syntax.*
import org.fiume.sketch.datastore.http.Model.Incorrect
import org.fiume.sketch.domain.Document

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

  object Incorrects:
    given Encoder[Incorrect.Detail] = Encoder.instance {
      case missing @ Incorrect.Missing(_)     => missing.asJson
      case malformed @ Incorrect.Malformed(_) => malformed.asJson
    }

    given Encoder[Incorrect.Missing] = new Encoder[Incorrect.Missing]:
      override def apply(missing: Incorrect.Missing): Json = Json.obj(
        "missing" -> missing.field.asJson
      )

    given Encoder[Incorrect.Malformed] = new Encoder[Incorrect.Malformed]:
      override def apply(malformed: Incorrect.Malformed): Json = Json.obj(
        "malformed" -> malformed.description.asJson
      )

    given Encoder[Incorrect] = new Encoder[Incorrect]:
      override def apply(incorrect: Incorrect): Json = Json.obj(
        "incorrect" -> incorrect.details.asJson
      )

    given Decoder[Incorrect.Detail] = List(
      Decoder[Incorrect.Missing].widen,
      Decoder[Incorrect.Malformed].widen
    ).reduceLeft(_ or _)

    given Decoder[Incorrect.Missing] =
      Decoder[String].at("missing").map(Incorrect.Missing.apply)

    given Decoder[Incorrect.Malformed] =
      Decoder[String].at("malformed").map(Incorrect.Malformed.apply)

    given Decoder[Incorrect] =
      Decoder[NonEmptyChain[Incorrect.Detail]].at("incorrect").map(Incorrect.apply)
