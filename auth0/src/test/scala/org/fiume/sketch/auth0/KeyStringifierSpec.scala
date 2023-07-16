package org.fiume.sketch.auth0

import munit.ScalaCheckSuite
import org.fiume.sketch.auth0.support.EcKeysGens
import org.fiume.sketch.shared.test.EitherSyntax.*
import org.scalacheck.Gen
import org.scalacheck.Prop.forAll

import java.security.{KeyPairGenerator, SecureRandom}
import java.util.Base64

class KeyStringifierSpec extends ScalaCheckSuite with EcKeysGens:

  property("isomorphism between toPemString and fromPemString for ECPrivateKey"):
    forAll(ecKeyPairs) { case (privateKey, _) =>
      val pemString = KeyStringifier.toPemString(privateKey)
      val result = KeyStringifier.ecPrivateKeyFromPem(pemString).rightValue
      assertEquals(result, privateKey)
    }

  property("isomorphism between toPemString and fromPemString for ECPublicKey"):
    forAll(ecKeyPairs) { case (_, publicKey) =>
      val pemString = KeyStringifier.toPemString(publicKey)
      val result = KeyStringifier.ecPublicKeyFromPem(pemString).rightValue
      assertEquals(result, publicKey)
    }

  property("fromPemString should fail for invalid private key pem string"):
    forAll { (invalidPemString: String) =>
      val result = KeyStringifier.ecPrivateKeyFromPem(invalidPemString)
      assert(result.isLeft)
    }

  property("fromPemString should fail for invalid public key pem string"):
    forAll { (invalidPemString: String) =>
      val result = KeyStringifier.ecPublicKeyFromPem(invalidPemString)
      assert(result.isLeft)
    }
