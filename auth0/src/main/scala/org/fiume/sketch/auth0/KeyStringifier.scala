package org.fiume.sketch.auth0

import io.circe.{Encoder, Json}
import io.circe.syntax.*

import java.security.interfaces.{ECPrivateKey, ECPublicKey}
import java.util.Base64

object KeyStringifier:
  def toPemString(privateKey: ECPrivateKey): String =
    val privateKeyBase64 = Base64.getEncoder.encodeToString(privateKey.getEncoded)
    s"-----BEGIN PRIVATE KEY-----\n$privateKeyBase64\n-----END PRIVATE KEY-----"

  def toPemString(publicKey: ECPublicKey): String =
    val publicKeyBase64 = Base64.getEncoder.encodeToString(publicKey.getEncoded)
    s"-----BEGIN PUBLIC KEY-----\n$publicKeyBase64\n-----END PUBLIC KEY-----"

  
  /*
   * Experimetal code, particularly the JWK part. It's not supposed to be used in production.
   */
  def toJwkString(key: ECPublicKey): String =
    val jwk = Json.obj(
      "kty" -> "EC".asJson,
      "crv" -> "P-512".asJson,
      "x" -> key.getW.getAffineX.toString(16).asJson,
      "y" -> key.getW.getAffineY.toString(16).asJson
    )
    jwk.noSpaces

  def toJwkString(key: ECPrivateKey): String =
    val jwk = Json.obj(
      "kty" -> "EC".asJson,
      "crv" -> "P-512".asJson,
      "x" -> key.getParams.getGenerator.getAffineX.toString(16).asJson,
      "y" -> key.getParams.getGenerator.getAffineY.toString(16).asJson,
      "d" -> key.getS.toString(16).asJson
    )
    jwk.noSpaces
