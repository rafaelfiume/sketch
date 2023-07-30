package org.fiume.sketch.shared.app.troubleshooting

import cats.Semigroup
import cats.implicits.*
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo.*

import java.time.ZonedDateTime

// Let's adopt a flat structure and shere it leads us
case class ErrorInfo(code: ErrorCode, message: ErrorMessage, details: Option[ErrorDetails])
case class ErrorMessage(value: String) extends AnyVal
case class ErrorDetails(tips: Map[String, String]) extends AnyVal

// TODO Define a proper error code hierarchy
enum ErrorCode:
  case InvalidClientInput
  case InvalidUserCredentials
  case InvalidDocument

object ErrorInfo:
  def short(code: ErrorCode, message: ErrorMessage): ErrorInfo = ErrorInfo(code, message, None)

  def withDetails(code: ErrorCode, message: ErrorMessage, details: ErrorDetails): ErrorInfo =
    ErrorInfo(code, message, Some(details))

  given Semigroup[ErrorDetails] = new Semigroup[ErrorDetails]:
    def combine(x: ErrorDetails, y: ErrorDetails): ErrorDetails = ErrorDetails(x.tips.combine(y.tips))
