package org.fiume.sketch.shared.auth.testkit

import org.fiume.sketch.shared.auth.domain.{Jwt, JwtVerificationError}
import org.fiume.sketch.shared.auth.domain.JwtVerificationError.*
import org.scalacheck.{Arbitrary, Gen}

object JwtGens:

  given Arbitrary[Jwt] = Arbitrary(jwts)
  def jwts: Gen[Jwt] = Gen
    .const(
      "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"
    )
    .map(Jwt.makeUnsafeFromString)

  given Arbitrary[JwtVerificationError] = Arbitrary(jwtErrors)
  def jwtErrors: Gen[JwtVerificationError] =
    val d = "a jwt error details"
    Gen
      .oneOf(
        JwtExpirationError(d),
        JwtEmptySignatureError(d),
        JwtInvalidTokenError(d),
        JwtValidationError(d),
        JwtUnknownError(d)
      )
      .sample
      .get
