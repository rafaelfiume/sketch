package org.fiume.sketch.auth0

import cats.effect.{Clock, IO}
import cats.implicits.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import munit.Assertions.*
import org.fiume.sketch.auth0.test.EcKeysGens
import org.fiume.sketch.shared.auth0.User
import org.fiume.sketch.shared.auth0.test.UserGens.*
import org.fiume.sketch.shared.test.ClockContext
import org.fiume.sketch.shared.test.EitherSyntax.*
import org.fiume.sketch.shared.test.Gens.DateAndTime.*
import org.scalacheck.ShrinkLowPriority
import org.scalacheck.effect.PropF.forAllF

import java.time.ZonedDateTime

class JwtSpec extends CatsEffectSuite with ScalaCheckEffectSuite with ClockContext with EcKeysGens with ShrinkLowPriority:

  test("create and parse jwt token"):
    forAllF(ecKeyPairs, users, shortDurations) { case ((privateKey, publicKey), user, expirationOffset) =>
      for
        jwtToken <- JwtToken.createJwtToken[IO](privateKey, user, expirationOffset)

        result = JwtToken.verifyJwtToken(jwtToken, publicKey)

        _ <- IO { assertEquals(result.rightValue, user) }
      yield ()
    }

  test("expired jwt token"):
    forAllF(ecKeyPairs, users, shortDurations) { case ((privateKey, publicKey), user, expirationOffset) =>
      given Clock[IO] = makeFrozenTime(ZonedDateTime.now().minusSeconds(expirationOffset.toSeconds))
      for
        jwtToken <- JwtToken.createJwtToken[IO](privateKey, user, expirationOffset)

        result = JwtToken.verifyJwtToken(jwtToken, publicKey)

        _ <- IO { assert(result.leftValue.getMessage().contains("The token is expired since ")) }
      yield ()
    }

  test("wrong public key"):
    forAllF(ecKeyPairs, ecKeyPairs, users, shortDurations) {
      case ((privateKey, _), (_, strangePublicKey), user, expirationOffset) =>
        for
          jwtToken <- JwtToken.createJwtToken[IO](privateKey, user, expirationOffset)

          result = JwtToken.verifyJwtToken(jwtToken, strangePublicKey)
          _ <- IO {
            assertEquals(result.leftValue.getMessage(), "Invalid signature for this token or wrong algorithm.")
          }
        yield ()
    }
