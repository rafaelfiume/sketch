package org.fiume.sketch.shared.app.http // TODO move it from `http` to `troubleshooting`

import org.fiume.sketch.shared.app.ErrorCode
import org.fiume.sketch.shared.app.ErrorCode.*

import java.time.ZonedDateTime

// TODO: make it a even more error info by storing in the auditing module? Perhaps enriched with extra info?
object Model: // TODO Rename to ErrorInfo
  case class ErrorInfo(code: ErrorCode, message: ErrorMessage, details: Option[ErrorDetails], timestamp: Option[ZonedDateTime])
  case class ErrorMessage(value: String) extends AnyVal
  case class ErrorDetails(value: String) extends AnyVal

  object ErrorInfo:
    def apply(code: ErrorCode, message: ErrorMessage): ErrorInfo = ErrorInfo(code, message, None, None)

    def withDetails(code: ErrorCode, message: ErrorMessage, details: ErrorDetails): ErrorInfo =
      ErrorInfo(code, message, Some(details), None)
