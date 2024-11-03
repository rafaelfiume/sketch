package org.fiume.sketch.shared.auth.domain

import cats.Show

enum JwtVerificationError(val details: String):
  // TODO Rename to conform to standards, e.g ExpiredToken
  case JwtExpirationError(override val details: String) extends JwtVerificationError(details)

  case JwtEmptySignatureError(override val details: String) extends JwtVerificationError(details)

  case JwtInvalidTokenError(override val details: String) extends JwtVerificationError(details)

  case JwtValidationError(override val details: String) extends JwtVerificationError(details)

  case JwtUnknownError(override val details: String) extends JwtVerificationError(details)

object JwtVerificationError:
  given Show[JwtVerificationError] = Show.show(error => s"${error.getClass.getSimpleName}(${error.details})")
