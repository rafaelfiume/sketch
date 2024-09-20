package org.fiume.sketch.shared.auth0.testkit

import org.fiume.sketch.shared.auth0.domain.{JwtError, JwtToken}
import org.fiume.sketch.shared.auth0.domain.JwtError.*
import org.scalacheck.{Arbitrary, Gen}

object JwtTokenGens:

  given Arbitrary[JwtToken] = Arbitrary(jwtTokens)
  def jwtTokens: Gen[JwtToken] = Gen
    .const(
      "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"
    )
    .map(JwtToken.makeUnsafeFromString)

  given Arbitrary[JwtError] = Arbitrary(jwtErrors)
  def jwtErrors: Gen[JwtError] =
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
