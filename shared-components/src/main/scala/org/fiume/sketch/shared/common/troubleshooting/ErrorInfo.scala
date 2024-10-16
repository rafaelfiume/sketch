package org.fiume.sketch.shared.common.troubleshooting

import cats.Semigroup
import cats.implicits.*
import org.fiume.sketch.shared.common.troubleshooting.ErrorInfo.{ErrorCode, ErrorDetails, ErrorMessage}

case class ErrorInfo private (
  code: ErrorCode,
  message: ErrorMessage,
  details: Option[ErrorDetails]
)

object ErrorInfo:
  def make(code: ErrorCode, message: ErrorMessage): ErrorInfo = ErrorInfo(code, message, None)

  def make(code: ErrorCode, message: ErrorMessage, details: ErrorDetails): ErrorInfo =
    ErrorInfo(code, message, Some(details))

  case class ErrorCode(value: String) extends AnyVal
  case class ErrorMessage(value: String) extends AnyVal
  case class ErrorDetails private (tips: Map[String, String]) extends AnyVal

  object ErrorDetails:
    def apply(details: (String, String)*): ErrorDetails = ErrorDetails(details.toMap)

    // TODO Test this
    given Semigroup[ErrorDetails] with
      def combine(x: ErrorDetails, y: ErrorDetails): ErrorDetails = ErrorDetails(x.tips.combine(y.tips))

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
