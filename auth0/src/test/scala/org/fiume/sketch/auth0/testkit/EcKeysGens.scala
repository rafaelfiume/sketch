package org.fiume.sketch.auth0.testkit

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.fiume.sketch.auth0.KeysGenerator
import org.scalacheck.{Arbitrary, Gen}

import java.security.interfaces.{ECPrivateKey, ECPublicKey}

trait EcKeysGens:

  given IORuntime = IORuntime.global

  given Arbitrary[(ECPrivateKey, ECPublicKey)] = Arbitrary(ecKeyPairs)
  def ecKeyPairs: Gen[(ECPrivateKey, ECPublicKey)] =
    Gen.delay(KeysGenerator.makeEcKeyPairs[IO]().unsafeRunSync())
