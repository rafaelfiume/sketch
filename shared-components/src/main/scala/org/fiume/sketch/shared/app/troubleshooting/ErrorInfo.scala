package org.fiume.sketch.shared.app.troubleshooting

import cats.Semigroup
import cats.implicits.*
import org.fiume.sketch.shared.typeclasses.AsString

// Let's adopt a flat structure and shere it leads us
case class ErrorInfo(message: ErrorMessage, details: Option[ErrorDetails])
case class ErrorMessage(value: String) extends AnyVal
case class ErrorDetails(tips: Map[String, String]) extends AnyVal

object ErrorInfo:
  def short(message: ErrorMessage): ErrorInfo = ErrorInfo(message, None)

  def withDetails(message: ErrorMessage, details: ErrorDetails): ErrorInfo =
    ErrorInfo(message, Some(details))

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

object ErrorMessage:
  extension (msg: ErrorMessage) def asString: String = s"${msg.value}"

object ErrorDetails:
  def single(detail: (String, String)) = ErrorDetails(Map(detail))

  extension (details: ErrorDetails) def asString: String = details.tips.mkString(" * ", "\n * ", "")
