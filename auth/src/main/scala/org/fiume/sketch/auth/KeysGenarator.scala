package org.fiume.sketch.auth

import cats.effect.Sync

import java.security.KeyPairGenerator
import java.security.interfaces.{ECPrivateKey, ECPublicKey}
import java.security.spec.ECGenParameterSpec

object KeysGenerator:
  def makeEcKeyPairs[F[_]: Sync](): F[(ECPrivateKey, ECPublicKey)] =
    Sync[F].blocking { // cpu-bound blocking operation
      /* Ensure thread-safety by instantiating a new KeyPairGenerator every time asymetric keys are created */
      val keyPairGenerator = KeyPairGenerator.getInstance("EC", "BC")
      keyPairGenerator.initialize(new ECGenParameterSpec("P-256"))
      val keyPair = keyPairGenerator.generateKeyPair()
      (keyPair.getPrivate().asInstanceOf[ECPrivateKey], keyPair.getPublic().asInstanceOf[ECPublicKey])
    }
