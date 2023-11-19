package org.fiume.sketch.shared.app.troubleshooting

import cats.Semigroup
import cats.implicits.*
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo.{ErrorDetails, ErrorMessage}
import org.fiume.sketch.shared.typeclasses.AsString

case class ErrorInfo(message: ErrorMessage, details: Option[ErrorDetails])

object ErrorInfo:
  def short(message: ErrorMessage): ErrorInfo = ErrorInfo(message, None)

  def withDetails(message: ErrorMessage, details: ErrorDetails): ErrorInfo =
    ErrorInfo(message, Some(details))

  case class ErrorMessage(value: String) extends AnyVal
  object ErrorMessage:
    extension (msg: ErrorMessage) def asString: String = s"${msg.value}"

  case class ErrorDetails(tips: Map[String, String]) extends AnyVal
  object ErrorDetails:
    def single(detail: (String, String)) = ErrorDetails(Map(detail))
    extension (details: ErrorDetails) def asString: String = details.tips.mkString(" * ", "\n * ", "")
    given Semigroup[ErrorDetails] = new Semigroup[ErrorDetails]:
      def combine(x: ErrorDetails, y: ErrorDetails): ErrorDetails = ErrorDetails(x.tips.combine(y.tips))

  given AsString[ErrorInfo] = new AsString[ErrorInfo]:
    extension (error: ErrorInfo)
      override def asString(): String =
        val semanticErrorMessage = error.message.asString
        error.details.fold(
          ifEmpty = semanticErrorMessage
        ) { details =>
          s"""|${semanticErrorMessage}:
            |${details.asString}""".stripMargin
        }

  object json:
    import io.circe.{Decoder, Encoder, HCursor, Json}
    import io.circe.Decoder.Result
    import io.circe.syntax.*

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
