package org.fiume.sketch.shared.auth0.domain

import cats.Show
import cats.implicits.*

import scala.util.control.NoStackTrace

case class JwtToken private (value: String) extends AnyVal

object JwtToken:
  def makeUnsafeFromString(value: String): JwtToken = new JwtToken(value)

sealed trait JwtError extends Throwable with NoStackTrace:
  def details: String
  override def toString(): String = this.show

object JwtError:
  given Show[JwtError] = Show.show(error => s"${error.getClass.getSimpleName}(${error.details})")

  case class JwtExpirationError(details: String) extends JwtError // Rename to conform to standards, e.g ExpiredToken
  case class JwtEmptySignatureError(details: String) extends JwtError
  case class JwtInvalidTokenError(details: String) extends JwtError
  case class JwtValidationError(details: String) extends JwtError
  case class JwtUnknownError(details: String) extends JwtError
