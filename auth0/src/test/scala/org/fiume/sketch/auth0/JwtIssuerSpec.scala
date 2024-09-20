package org.fiume.sketch.auth0

import cats.effect.IO
import munit.Assertions.*
import munit.ScalaCheckSuite
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.fiume.sketch.auth0.testkit.EcKeysGens
import org.fiume.sketch.shared.auth0.domain.JwtError.*
import org.fiume.sketch.shared.auth0.domain.JwtToken
import org.fiume.sketch.shared.auth0.testkit.UserGens.*
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
      val jwtToken = JwtIssuer.make[IO](privateKey, user, now, expirationOffset)

      val result = JwtIssuer.verify(jwtToken, publicKey)
//
      result.rightOrFail == user
    }

  test("invalid jwt verification fails"):
    forAll(ecKeyPairs, users, shortDurations) { case ((privateKey, publicKey), user, expirationOffset) =>
      val jwtToken = JwtIssuer.make[IO](privateKey, user, now, expirationOffset)

      val result = JwtIssuer.verify(
        JwtToken.makeUnsafeFromString(s"${jwtToken.value}wrong"),
        publicKey
      )

      assert(result.leftOrFail.isInstanceOf[JwtValidationError])
      assertEquals(result.leftOrFail.details, "Invalid signature for this token or wrong algorithm.")
    }

  test("expired jwt verification fails"):
    forAll(ecKeyPairs, users, shortDurations) { case ((privateKey, publicKey), user, expirationOffset) =>
      val before = Instant.now().minusSeconds(expirationOffset.toSeconds)
      val jwtToken = JwtIssuer.make[IO](privateKey, user, before, expirationOffset)

      val result = JwtIssuer.verify(jwtToken, publicKey)

      assert(result.leftOrFail.isInstanceOf[JwtExpirationError])
      assert(result.leftOrFail.details.contains("The token is expired since "))
    }

  test("token verification with invalid public key fails"):
    forAll(ecKeyPairs, ecKeyPairs, users, shortDurations) {
      case ((privateKey, _), (_, strangePublicKey), user, expirationOffset) =>
        val jwtToken = JwtIssuer.make[IO](privateKey, user, now, expirationOffset)

        val result = JwtIssuer.verify(jwtToken, strangePublicKey)

        assert(result.leftOrFail.isInstanceOf[JwtValidationError])
        assertEquals(result.leftOrFail.details, "Invalid signature for this token or wrong algorithm.")
    }

trait JwtTokenSpecContext:
  val now = Instant.now()
