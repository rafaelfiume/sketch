package org.fiume.sketch.shared.app.troubleshooting

import cats.Semigroup
import cats.implicits.*
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo.{ErrorDetails, ErrorMessage}
import org.fiume.sketch.shared.typeclasses.AsString

case class ErrorInfo(code: ErrorCode, message: ErrorMessage, details: Option[ErrorDetails])

case class ErrorCode(value: String) extends AnyVal

object ErrorInfo:
  def make(code: ErrorCode, message: ErrorMessage): ErrorInfo = ErrorInfo(code, message, None)

  def make(code: ErrorCode, message: ErrorMessage, details: ErrorDetails): ErrorInfo =
    ErrorInfo(code, message, Some(details))

  case class ErrorMessage(value: String) extends AnyVal
  object ErrorMessage:
    extension (msg: ErrorMessage) def asString: String = s"${msg.value}"

  case class ErrorDetails private (tips: Map[String, String]) extends AnyVal
  object ErrorDetails:
    def apply(details: (String, String)*): ErrorDetails = ErrorDetails(details.toMap)

    extension (d: ErrorDetails) def asString: String = d.tips.mkString(" * ", "\n * ", "")

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
    import io.circe.{Decoder, Encoder}
    import io.circe.generic.semiauto.*

    given Encoder[ErrorCode] = Encoder.encodeString.contramap(_.value)
    given Decoder[ErrorCode] = Decoder.decodeString.map(ErrorCode.apply)
    given Encoder[ErrorMessage] = Encoder.encodeString.contramap(_.value)
    given Decoder[ErrorMessage] = Decoder.decodeString.map(ErrorMessage.apply)
    given Encoder[ErrorDetails] = Encoder.encodeMap[String, String].contramap(_.tips)
    given Decoder[ErrorDetails] = Decoder.decodeMap[String, String].map(_.toList).map(ErrorDetails.apply)
    given Encoder[ErrorInfo] = deriveEncoder
    given Decoder[ErrorInfo] = deriveDecoder
