package org.fiume.sketch.auth0

import cats.effect.Sync
import cats.implicits.*
import org.bouncycastle.jce.provider.BouncyCastleProvider

import java.io.StringWriter
import java.security.{KeyPairGenerator, Security}
import java.security.interfaces.{ECPrivateKey, ECPublicKey}
import java.security.spec.ECGenParameterSpec

object KeysGenerator:
  Security.addProvider(new BouncyCastleProvider())

  def makeEcKeyPairs[F[_]]()(using F: Sync[F]): F[(ECPrivateKey, ECPublicKey)] =
    F.blocking {
      /* Ensure thread-safety by instantiating a new KeyPairGenerator every time asymetric keys are created (cpu-bound) */
      val keyPairGenerator = KeyPairGenerator.getInstance("EC", "BC")
      keyPairGenerator.initialize(new ECGenParameterSpec("P-521"))
      val keyPair = keyPairGenerator.generateKeyPair()
      (keyPair.getPrivate().asInstanceOf[ECPrivateKey], keyPair.getPublic().asInstanceOf[ECPublicKey])
    }
