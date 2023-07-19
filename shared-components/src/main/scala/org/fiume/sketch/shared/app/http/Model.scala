package org.fiume.sketch.shared.app.http

import java.time.ZonedDateTime

object Model:
  case class ErrorCode(value: String) extends AnyVal
  case class ErrorMessage(value: String) extends AnyVal
  case class ErrorDetails(value: String) extends AnyVal
  case class ErrorInfo(code: ErrorCode, message: ErrorMessage, details: Option[ErrorDetails], timestamp: Option[ZonedDateTime])

  object ErrorInfo:
    def apply(code: ErrorCode, message: ErrorMessage): ErrorInfo = ErrorInfo(code, message, None, None)

    def withDetails(code: ErrorCode, message: ErrorMessage, details: ErrorDetails): ErrorInfo =
      ErrorInfo(code, message, Some(details), None)
