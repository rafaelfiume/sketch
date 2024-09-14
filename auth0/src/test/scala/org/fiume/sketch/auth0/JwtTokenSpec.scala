package org.fiume.sketch.auth0

import cats.effect.{Clock, IO}
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import munit.Assertions.*
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.fiume.sketch.auth0.JwtError.*
import org.fiume.sketch.auth0.testkit.EcKeysGens
import org.fiume.sketch.shared.auth0.User
import org.fiume.sketch.shared.auth0.testkit.UserGens.*
import org.fiume.sketch.shared.testkit.ClockContext
import org.fiume.sketch.shared.testkit.Gens.DateAndTime.*
import org.fiume.sketch.shared.testkit.syntax.EitherSyntax.*
import org.scalacheck.ShrinkLowPriority
import org.scalacheck.effect.PropF.forAllF

import java.security.Security
import java.time.ZonedDateTime

class JwtTokenSpec extends CatsEffectSuite with ScalaCheckEffectSuite with ClockContext with EcKeysGens with ShrinkLowPriority:

  Security.addProvider(new BouncyCastleProvider())

  test("valid jwt verification results in the user details"):
    forAllF(ecKeyPairs, users, shortDurations) { case ((privateKey, publicKey), user, expirationOffset) =>
      for
        jwtToken <- JwtToken.makeJwtToken[IO](privateKey, user, expirationOffset)

        result = JwtToken.verifyJwtToken(jwtToken, publicKey)
//
      yield assertEquals(result.rightOrFail, user)
    }

  test("invalid jwt verification fails"):
    forAllF(ecKeyPairs, users, shortDurations) { case ((privateKey, publicKey), user, expirationOffset) =>
      for
        jwtToken <- JwtToken.makeJwtToken[IO](privateKey, user, expirationOffset)

        result = JwtToken.verifyJwtToken(
          JwtToken.notValidatedFromString(s"${jwtToken.value}wrong"),
          publicKey
        )
      yield
        assert(result.leftOfFail.isInstanceOf[JwtValidationError])
        assertEquals(result.leftOfFail.details, "Invalid signature for this token or wrong algorithm.")
    }

  test("expired jwt verification fails"):
    forAllF(ecKeyPairs, users, shortDurations) { case ((privateKey, publicKey), user, expirationOffset) =>
      given Clock[IO] = makeFrozenTime(ZonedDateTime.now().minusSeconds(expirationOffset.toSeconds))
      for
        jwtToken <- JwtToken.makeJwtToken[IO](privateKey, user, expirationOffset)

        result = JwtToken.verifyJwtToken(jwtToken, publicKey)
      yield
        assert(result.leftOfFail.isInstanceOf[JwtExpirationError])
        assert(result.leftOfFail.details.contains("The token is expired since "))
    }

  test("token verification with invalid public key fails"):
    forAllF(ecKeyPairs, ecKeyPairs, users, shortDurations) {
      case ((privateKey, _), (_, strangePublicKey), user, expirationOffset) =>
        for
          jwtToken <- JwtToken.makeJwtToken[IO](privateKey, user, expirationOffset)

          result = JwtToken.verifyJwtToken(jwtToken, strangePublicKey)
        yield
          assert(result.leftOfFail.isInstanceOf[JwtValidationError])
          assertEquals(result.leftOfFail.details, "Invalid signature for this token or wrong algorithm.")
    }
