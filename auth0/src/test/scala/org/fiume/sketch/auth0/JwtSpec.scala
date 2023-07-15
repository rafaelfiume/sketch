package org.fiume.sketch.auth0

import cats.effect.IO
import cats.implicits.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import munit.Assertions.*
import org.fiume.sketch.shared.auth0.Model.*
import org.fiume.sketch.shared.test.ClockContext
import org.fiume.sketch.shared.test.EitherSyntax.*
import org.scalacheck.ShrinkLowPriority
import org.scalacheck.effect.PropF.forAllF

import java.security.interfaces.{ECPrivateKey, ECPublicKey}
import java.time.ZonedDateTime
import java.util.Base64

class JwtSpec extends CatsEffectSuite with ScalaCheckEffectSuite with ClockContext with JwtSpecContext with ShrinkLowPriority:

  test("create and parse jwt token"):
    forAllF { (user: User, asymetricKeyPair: IO[(ECPrivateKey, ECPublicKey)]) =>
      for
        keys <- asymetricKeyPair
        (privateKey, publicKey) = (keys._1, keys._2)
        jwtToken <- JwtToken.createJwtToken[IO](privateKey, user)
        result = JwtToken.verifyJwtToken(jwtToken, publicKey).rightValue
        _ <- IO { assertEquals(result, user) } // TODO Check other fields
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
    forAllF { (user: User, asymetricKeyPair: IO[(ECPrivateKey, ECPublicKey)]) =>
      given cats.effect.Clock[IO] = makeFrozenTime(ZonedDateTime.now().minusDays(2))
      for
        keys <- asymetricKeyPair
        (privateKey, publicKey) = (keys._1, keys._2)
        jwtToken <- JwtToken.createJwtToken[IO](privateKey, user)
        result = JwtToken.verifyJwtToken(jwtToken, publicKey).leftValue
        _ <- IO { assert(result.contains("The token is expired since ")) }
      yield ()
    }

  test("wrong public key"):
    forAllF { (user: User, asymetricKeyPair: IO[(ECPrivateKey, ECPublicKey)], strangeKeyPair: IO[(ECPrivateKey, ECPublicKey)]) =>
      given cats.effect.Clock[IO] = makeFrozenTime(ZonedDateTime.now().minusDays(2))
      for
        privateKey <- asymetricKeyPair.map(_._1)
        strangePublicKey <- strangeKeyPair.map(_._2)
        jwtToken <- JwtToken.createJwtToken[IO](privateKey, user)
        result = JwtToken.verifyJwtToken(jwtToken, strangePublicKey).leftValue
        _ <- IO { assertEquals(result, "Invalid signature for this token or wrong algorithm.") }
      yield ()
    }

trait JwtSpecContext:

  // TODO Duplicated from PostgresUsersStoreSpecContext. Extract them to somewhere
  import org.fiume.sketch.shared.test.Gens
  import org.scalacheck.{Arbitrary, Gen}
  given Arbitrary[Username] = Arbitrary(usernames)
  def usernames: Gen[Username] = Gens.Strings.alphaNumString(1, 60).map(Username(_))

  given Arbitrary[User] = Arbitrary(users)
  def users: Gen[User] =
    for
      uuid <- Gen.uuid
      username <- usernames
    yield User(uuid, username)

  given Arbitrary[IO[(ECPrivateKey, ECPublicKey)]] = Arbitrary(asymetricKeyPairs)
  def asymetricKeyPairs: Gen[IO[(ECPrivateKey, ECPublicKey)]] = Gen.delay(KeysGenerator.makeEcKeyPairs[IO]())
