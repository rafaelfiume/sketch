package org.fiume.sketch.shared.app.troubleshooting // TODO move it from `http` to `troubleshooting`

import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo.*

import java.time.ZonedDateTime

// TODO Remove timestamp from ErrorInfo
case class ErrorInfo(code: ErrorCode, message: ErrorMessage, details: Option[ErrorDetails], timestamp: Option[ZonedDateTime])

object ErrorInfo:
  case class ErrorMessage(value: String) extends AnyVal
  case class ErrorDetails(values: Map[String, String]) extends AnyVal

  // TODO Rename apply
  def apply(code: ErrorCode, message: ErrorMessage): ErrorInfo = ErrorInfo(code, message, None, None)
  def withDetails(code: ErrorCode, message: ErrorMessage, details: ErrorDetails): ErrorInfo =
    ErrorInfo(code, message, Some(details), None)

  enum ErrorCode:
    case InvalidCredentials
