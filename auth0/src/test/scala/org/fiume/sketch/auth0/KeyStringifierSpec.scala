package org.fiume.sketch.auth0

import munit.ScalaCheckSuite
import org.fiume.sketch.auth0.testkit.EcKeysGens
import org.fiume.sketch.shared.testkit.EitherSyntax.*
import org.scalacheck.Prop.forAll

class KeyStringifierSpec extends ScalaCheckSuite with EcKeysGens:

  property("ECPrivateKey toPemString and fromPemString are isomorphic"):
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

  property("fromPemString fails for invalid private key pem string"):
    forAll { (invalidPemString: String) =>
      val result = KeyStringifier.ecPrivateKeyFromPem(invalidPemString)
      assert(result.isLeft)
    }

  property("fromPemString fails for invalid public key pem string"):
    forAll { (invalidPemString: String) =>
      val result = KeyStringifier.ecPublicKeyFromPem(invalidPemString)
      assert(result.isLeft)
    }
