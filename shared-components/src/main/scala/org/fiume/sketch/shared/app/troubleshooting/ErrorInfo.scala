package org.fiume.sketch.shared.app.troubleshooting // TODO move it from `http` to `troubleshooting`

import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo.*

import java.time.ZonedDateTime

case class ErrorInfo(code: ErrorCode, message: ErrorMessage, details: Option[ErrorDetails], timestamp: Option[ZonedDateTime])

object ErrorInfo:
  case class ErrorMessage(value: String) extends AnyVal
  case class ErrorDetails(values: Map[String, String]) extends AnyVal

  // TODO Rethink these factory functions
  def apply(code: ErrorCode, message: ErrorMessage): ErrorInfo = ErrorInfo(code, message, None, None)

  enum ErrorCode:
    case InvalidCredentials
