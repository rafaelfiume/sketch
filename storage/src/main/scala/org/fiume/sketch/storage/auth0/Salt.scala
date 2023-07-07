package org.fiume.sketch.storage.auth0

import cats.Show
import cats.effect.Sync
import cats.implicits.*

import java.util.Base64

sealed abstract case class Salt(base64Value: String):
  val bytes: Array[Byte] = Base64.getDecoder.decode(base64Value)

object Salt:
  val lengthInBytes = 32

  def generate[F[_]]()(using F: Sync[F]): F[Salt] =
    for
      // cryptographically secure random number generator
      random <- F.delay { new java.security.SecureRandom() }
      salt <- F.delay {
        val bytes = new Array[Byte](lengthInBytes)
        random.nextBytes(bytes)
        Base64.getEncoder.encodeToString(bytes)
      }
    yield Salt.unsafeFromString(salt)

  def unsafeFromString(base64: String): Salt = new Salt(base64) {}

  given Show[Salt] = Show.fromToString
