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
import org.fiume.sketch.shared.testkit.Syntax.EitherSyntax.*
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
      yield assertEquals(result.rightValue, user)
    }

  test("invalid jwt verification fails"):
    forAllF(ecKeyPairs, users, shortDurations) { case ((privateKey, publicKey), user, expirationOffset) =>
      for
        jwtToken <- JwtToken.makeJwtToken[IO](privateKey, user, expirationOffset)

        result = JwtToken.verifyJwtToken(
          JwtToken.notValidatedFromString(s"${jwtToken.value}wrong"),
          publicKey
        )

        _ <- IO {
          assert(result.leftValue.isInstanceOf[JwtValidationError])
          assertEquals(result.leftValue.details, "Invalid signature for this token or wrong algorithm.")
        }
      yield ()
    }

  test("expired jwt verification fails"):
    forAllF(ecKeyPairs, users, shortDurations) { case ((privateKey, publicKey), user, expirationOffset) =>
      given Clock[IO] = makeFrozenTime(ZonedDateTime.now().minusSeconds(expirationOffset.toSeconds))
      for
        jwtToken <- JwtToken.makeJwtToken[IO](privateKey, user, expirationOffset)

        result = JwtToken.verifyJwtToken(jwtToken, publicKey)

        _ <- IO {
          assert(result.leftValue.isInstanceOf[JwtExpirationError])
          assert(result.leftValue.details.contains("The token is expired since "))
        }
      yield ()
    }

  test("token verification with invalid public key fails"):
    forAllF(ecKeyPairs, ecKeyPairs, users, shortDurations) {
      case ((privateKey, _), (_, strangePublicKey), user, expirationOffset) =>
        for
          jwtToken <- JwtToken.makeJwtToken[IO](privateKey, user, expirationOffset)

          result = JwtToken.verifyJwtToken(jwtToken, strangePublicKey)
          _ <- IO {
            assert(result.leftValue.isInstanceOf[JwtValidationError])
            assertEquals(result.leftValue.details, "Invalid signature for this token or wrong algorithm.")
          }
        yield ()
    }
