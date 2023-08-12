package org.fiume.sketch.shared.app.troubleshooting.http.json

import io.circe.{Decoder, Encoder, HCursor, Json}
import io.circe.Decoder.Result
import io.circe.syntax.*
import org.fiume.sketch.shared.app.troubleshooting.{ErrorDetails, ErrorInfo, ErrorMessage}

object ErrorInfoCodecs:
  given Encoder[ErrorMessage] = Encoder.encodeString.contramap(_.value)
  given Decoder[ErrorMessage] = Decoder.decodeString.map(ErrorMessage.apply)
  given Encoder[ErrorDetails] = Encoder.encodeMap[String, String].contramap(_.tips)
  given Decoder[ErrorDetails] = Decoder.decodeMap[String, String].map(ErrorDetails.apply)
  given Encoder[ErrorInfo] = new Encoder[ErrorInfo]:
    override def apply(errorInfo: ErrorInfo): Json =
      Json.obj(
        "message" -> errorInfo.message.asJson,
        "details" -> errorInfo.details.asJson
      )
  given Decoder[ErrorInfo] = new Decoder[ErrorInfo]:
    override def apply(c: HCursor): Result[ErrorInfo] =
      for
        message <- c.downField("message").as[ErrorMessage]
        details <- c.downField("details").as[Option[ErrorDetails]]
      yield ErrorInfo(message, details)
