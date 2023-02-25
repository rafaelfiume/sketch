package org.fiume.sketch.storage.http

import cats.data.NonEmptyChain
import cats.implicits.*
import io.circe.{Decoder, Encoder, HCursor, Json}
import io.circe.Decoder.Result
import io.circe.syntax.*
import org.fiume.sketch.storage.http.Model.Incorrect

object JsonCodecs:

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
