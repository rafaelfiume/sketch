package org.fiume.sketch.auth0

import cats.effect.IO
import cats.implicits.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import munit.Assertions.*
import org.fiume.sketch.auth0.support.EcKeysGens
import org.fiume.sketch.shared.auth0.Model.User
import org.fiume.sketch.shared.auth0.support.UserGens
import org.fiume.sketch.shared.test.ClockContext
import org.fiume.sketch.shared.test.EitherSyntax.*
import org.scalacheck.ShrinkLowPriority
import org.scalacheck.effect.PropF.forAllF

import java.time.ZonedDateTime

class JwtSpec
    extends CatsEffectSuite
    with ScalaCheckEffectSuite
    with ClockContext
    with EcKeysGens
    with UserGens
    with ShrinkLowPriority:

  test("create and parse jwt token"):
    forAllF(ecKeyPairs, users) { case ((privateKey, publicKey), user) =>
      for
        jwtToken <- JwtToken.createJwtToken[IO](privateKey, user)
        result = JwtToken.verifyJwtToken(jwtToken, publicKey).rightValue
        _ <- IO { assertEquals(result, user) }
        _ <- IO {
          println("jwtToken:")
          println(jwtToken)
          println("publicKey:")
          println(KeyStringifier.toPemString(publicKey))
          println("privateKey:")
          println(KeyStringifier.toPemString(privateKey))
        }
      yield ()
    }

  test("expired jwt token"):
    forAllF(ecKeyPairs, users) { case ((privateKey, publicKey), user) =>
      given cats.effect.Clock[IO] = makeFrozenTime(ZonedDateTime.now().minusDays(2))
      for
        jwtToken <- JwtToken.createJwtToken[IO](privateKey, user)
        result = JwtToken.verifyJwtToken(jwtToken, publicKey).leftValue
        _ <- IO { assert(result.contains("The token is expired since ")) }
      yield ()
    }

  test("wrong public key"):
    forAllF(ecKeyPairs, ecKeyPairs, users) { case ((privateKey, _), (_, strangePublicKey), user) =>
      given cats.effect.Clock[IO] = makeFrozenTime(ZonedDateTime.now().minusDays(2))
      for
        jwtToken <- JwtToken.createJwtToken[IO](privateKey, user)
        result = JwtToken.verifyJwtToken(jwtToken, strangePublicKey).leftValue
        _ <- IO { assertEquals(result, "Invalid signature for this token or wrong algorithm.") }
      yield ()
    }

  // TODO Duplicated from PostgresUsersStoreSpecContext. Extract them to somewhere
