package org.fiume.sketch.shared.app.troubleshooting

import cats.Semigroup
import cats.implicits.*
import org.fiume.sketch.shared.app.typeclasses.ToSemanticString
import org.fiume.sketch.shared.app.typeclasses.ToSemanticStringSyntax.*

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

  given ToSemanticString[ErrorInfo] = new ToSemanticString[ErrorInfo]:
    def toSemanticString(error: ErrorInfo): String =
      val semanticErrorMessage = error.message.asSemanticString()
      error.details.fold(
        ifEmpty = semanticErrorMessage
      ) { details =>
        s"""|${semanticErrorMessage}:
            |${details.asSemanticString()}""".stripMargin
      }

object ErrorMessage:
  given ToSemanticString[ErrorMessage] = new ToSemanticString[ErrorMessage]:
    def toSemanticString(msg: ErrorMessage): String = s"${msg.value}"

object ErrorDetails:
  def single(detail: (String, String)) = ErrorDetails(Map(detail))

  given ToSemanticString[ErrorDetails] = new ToSemanticString[ErrorDetails]:
    def toSemanticString(dtl: ErrorDetails): String =
      dtl.tips.mkString(" * ", "\n * ", "")
