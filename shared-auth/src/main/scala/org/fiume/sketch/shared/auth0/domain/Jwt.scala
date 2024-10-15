package org.fiume.sketch.shared.auth.domain

import cats.Show
import cats.implicits.*

case class Jwt private (value: String) extends AnyVal

/*
 * Pronounced as "jot".
 */
object Jwt:
  def makeUnsafeFromString(value: String): Jwt = new Jwt(value)

enum JwtError(val details: String):
  // TODO Rename to conform to standards, e.g ExpiredToken
  case JwtExpirationError(override val details: String) extends JwtError(details)

  case JwtEmptySignatureError(override val details: String) extends JwtError(details)

  case JwtInvalidTokenError(override val details: String) extends JwtError(details)

  case JwtValidationError(override val details: String) extends JwtError(details)

  case JwtUnknownError(override val details: String) extends JwtError(details)

object JwtError:
  given Show[JwtError] = Show.show(error => s"${error.getClass.getSimpleName}(${error.details})")
