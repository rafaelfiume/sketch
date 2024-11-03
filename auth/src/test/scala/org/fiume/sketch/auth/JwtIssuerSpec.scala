package org.fiume.sketch.auth

import cats.effect.IO
import munit.Assertions.*
import munit.ScalaCheckSuite
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.fiume.sketch.auth.testkit.EcKeysGens
import org.fiume.sketch.shared.auth.domain.Jwt
import org.fiume.sketch.shared.auth.domain.JwtVerificationError.*
import org.fiume.sketch.shared.auth.testkit.UserGens.*
import org.fiume.sketch.shared.testkit.ClockContext
import org.fiume.sketch.shared.testkit.Gens.DateAndTime.*
import org.fiume.sketch.shared.testkit.syntax.EitherSyntax.*
import org.scalacheck.Prop.forAll
import org.scalacheck.ShrinkLowPriority

import java.security.Security
import java.time.Instant

class JwtIssuerSpec extends ScalaCheckSuite with ClockContext with EcKeysGens with JwtTokenSpecContext with ShrinkLowPriority:

  Security.addProvider(new BouncyCastleProvider())

  test("valid jwt verification results in the user details"):
    forAll(ecKeyPairs, users, shortDurations) { case ((privateKey, publicKey), user, expirationOffset) =>
      val jwt = JwtIssuer.make[IO](privateKey, user, now, expirationOffset)

      val result = JwtIssuer.verify(jwt, publicKey)
//
      result.rightOrFail == user
    }

  test("invalid jwt verification fails"):
    forAll(ecKeyPairs, users, shortDurations) { case ((privateKey, publicKey), user, expirationOffset) =>
      val jwt = JwtIssuer.make[IO](privateKey, user, now, expirationOffset)

      val result = JwtIssuer.verify(
        Jwt.makeUnsafeFromString(s"${jwt.value}wrong"),
        publicKey
      )

      assert(result.leftOrFail.isInstanceOf[JwtValidationError])
      assertEquals(result.leftOrFail.details, "Invalid signature for this token or wrong algorithm.")
    }

  test("expired jwt verification fails"):
    forAll(ecKeyPairs, users, shortDurations) { case ((privateKey, publicKey), user, expirationOffset) =>
      val before = Instant.now().minusSeconds(expirationOffset.toSeconds)
      val jwt = JwtIssuer.make[IO](privateKey, user, before, expirationOffset)

      val result = JwtIssuer.verify(jwt, publicKey)

      assert(result.leftOrFail.isInstanceOf[JwtExpirationError])
      assert(result.leftOrFail.details.contains("The token is expired since "))
    }

  test("token verification with invalid public key fails"):
    forAll(ecKeyPairs, ecKeyPairs, users, shortDurations) {
      case ((privateKey, _), (_, strangePublicKey), user, expirationOffset) =>
        val jwt = JwtIssuer.make[IO](privateKey, user, now, expirationOffset)

        val result = JwtIssuer.verify(jwt, strangePublicKey)

        assert(result.leftOrFail.isInstanceOf[JwtValidationError])
        assertEquals(result.leftOrFail.details, "Invalid signature for this token or wrong algorithm.")
    }

trait JwtTokenSpecContext:
  val now = Instant.now()
