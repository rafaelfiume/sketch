package org.fiume.sketch.storage.auth0

import cats.Show
import cats.effect.Sync
import cats.implicits.*
import org.mindrot.jbcrypt.BCrypt

import java.util.Base64

sealed abstract case class Salt(base64Value: String)

object Salt:
  val logRounds = 12

  def generate[F[_]]()(using F: Sync[F]): F[Salt] = F.delay { BCrypt.gensalt(logRounds) }.map(Salt.unsafeFromString)

  def unsafeFromString(base64: String): Salt = new Salt(base64) {}

  given Show[Salt] = Show.fromToString
