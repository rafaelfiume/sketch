package org.fiume.sketch.auth0

// TODO Clean up imports
import cats.effect.{Resource, Sync}
import cats.implicits.*
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.util.io.pem.{PemObject, PemWriter}

import java.io.StringWriter
import java.security.{KeyPairGenerator, PrivateKey, PublicKey, Security}
import java.security.interfaces.{ECPrivateKey, ECPublicKey}
import java.security.spec.ECGenParameterSpec

// TODO use Async[F]?
object KeysGenerator:
  Security.addProvider(new BouncyCastleProvider())

  def makeEcKeyPairs[F[_]]()(using F: Sync[F]): F[(ECPrivateKey, ECPublicKey)] =
    F.blocking {
      /* Ensure thread-safety by instantiating a new KeyPairGenerator every time asymetric keys are created */
      val keyPairGenerator = KeyPairGenerator.getInstance("EC", "BC")
      keyPairGenerator.initialize(new ECGenParameterSpec("P-521"))
      val keyPair = keyPairGenerator.generateKeyPair()
      (keyPair.getPrivate().asInstanceOf[ECPrivateKey], keyPair.getPublic().asInstanceOf[ECPublicKey])
    }
