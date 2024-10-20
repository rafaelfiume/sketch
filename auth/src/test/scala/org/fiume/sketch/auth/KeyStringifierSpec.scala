package org.fiume.sketch.auth

import munit.ScalaCheckSuite
import org.fiume.sketch.auth.testkit.EcKeysGens
import org.fiume.sketch.shared.testkit.syntax.EitherSyntax.*
import org.scalacheck.Prop.forAll

class KeyStringifierSpec extends ScalaCheckSuite with EcKeysGens:

  /* The conversion process should accurately represent a key object as a Pem string
   * and vice-versa, ensuring that key-related operations like signing
   * remain consistent across both formats.
   */

  property("ECPrivateKey toPemString and fromPemString form an isomorphism"):
    forAll(ecKeyPairs) { case (privateKey, _) =>
      val pemString = KeyStringifier.toPemString(privateKey)
      val result = KeyStringifier.ecPrivateKeyFromPem(pemString)
      assertEquals(result.rightOrFail, privateKey)
    }

  property("ECPublicKey toPemString and fromPemString form an isomorphism"):
    forAll(ecKeyPairs) { case (_, publicKey) =>
      val pemString = KeyStringifier.toPemString(publicKey)
      val result = KeyStringifier.ecPublicKeyFromPem(pemString)
      assertEquals(result.rightOrFail, publicKey)
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

  /* Note that "ECPrivateKey toPemString and fromPemString _are_ isomorphic"
   * is not correct because it implies that the functions themselves are isomorphic.
   * Isomorphism, however, is a property that relates two structures.
   */
