package org.fiume.sketch.shared.app.troubleshooting

import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo.*

import java.time.ZonedDateTime

case class ErrorInfo(code: ErrorCode, message: ErrorMessage, details: Option[ErrorDetails])

object ErrorInfo:
  case class ErrorMessage(value: String) extends AnyVal
  case class ErrorDetails(values: Map[String, String]) extends AnyVal

  def short(code: ErrorCode, message: ErrorMessage): ErrorInfo = ErrorInfo(code, message, None)
  def withDetails(code: ErrorCode, message: ErrorMessage, details: ErrorDetails): ErrorInfo =
    ErrorInfo(code, message, Some(details))

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
