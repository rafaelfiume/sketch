package org.fiume.sketch.auth0

import cats.implicits.*
import io.circe.{Encoder, Json}
import io.circe.syntax.*

import java.security.KeyFactory
import java.security.interfaces.{ECPrivateKey, ECPublicKey}
import java.security.spec.{PKCS8EncodedKeySpec, X509EncodedKeySpec}
import java.util.Base64

object KeyStringifier:
  def toPemString(privateKey: ECPrivateKey): String =
    val privateKeyBase64 = Base64.getEncoder.encodeToString(privateKey.getEncoded)
    s"-----BEGIN PRIVATE KEY-----\n$privateKeyBase64\n-----END PRIVATE KEY-----"

  def toPemString(publicKey: ECPublicKey): String =
    val publicKeyBase64 = Base64.getEncoder.encodeToString(publicKey.getEncoded)
    s"-----BEGIN PUBLIC KEY-----\n$publicKeyBase64\n-----END PUBLIC KEY-----"

  def ecPrivateKeyFromPem(pemString: String): Either[String, ECPrivateKey] = Either
    .catchNonFatal {
      val stripped = pemString.stripPrefix("-----BEGIN PRIVATE KEY-----\n").stripSuffix("\n-----END PRIVATE KEY-----")
      val bytes = Base64.getDecoder.decode(stripped)
      val keyFactory = KeyFactory.getInstance("EC", "BC")
      keyFactory.generatePrivate(new PKCS8EncodedKeySpec(bytes)).asInstanceOf[ECPrivateKey]
    }
    .leftMap(_.getMessage)

  def ecPublicKeyFromPem(pemString: String): Either[String, ECPublicKey] = Either
    .catchNonFatal {
      val stripped = pemString.stripPrefix("-----BEGIN PUBLIC KEY-----\n").stripSuffix("\n-----END PUBLIC KEY-----")
      val bytes = Base64.getDecoder.decode(stripped)
      val keyFactory = KeyFactory.getInstance("EC", "BC")
      keyFactory.generatePublic(new X509EncodedKeySpec(bytes)).asInstanceOf[ECPublicKey]
    }
    .leftMap(_.getMessage)

  /*
   * JWK is experimetal code. It's not supposed to be used in production.
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
