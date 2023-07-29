package org.fiume.sketch.shared.app.troubleshooting

import cats.Semigroup
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo.*

import java.time.ZonedDateTime

case class ErrorInfo(code: ErrorCode, message: ErrorMessage, details: Option[ErrorDetails])

object ErrorInfo:
  def short(code: ErrorCode, message: ErrorMessage): ErrorInfo = ErrorInfo(code, message, None)

  def withDetails(code: ErrorCode, message: ErrorMessage, details: ErrorDetails): ErrorInfo =
    ErrorInfo(code, message, Some(details))

  case class ErrorMessage(value: String) extends AnyVal

  case class ErrorDetails(tips: Map[String, String]) extends AnyVal
  object ErrorDetails:
    given Semigroup[ErrorDetails] = new Semigroup[ErrorDetails]:
      def combine(x: ErrorDetails, y: ErrorDetails): ErrorDetails = ErrorDetails(x.tips ++ y.tips)

  enum ErrorCode:
    /**
     * Entrypoints (e.g endpoints) *
     */
    case InvalidClientInput

    /**
     * Auth0 *
     */
    case InvalidUserCredentials

    /**
     * Documents *
     */
    case InvalidDocument
