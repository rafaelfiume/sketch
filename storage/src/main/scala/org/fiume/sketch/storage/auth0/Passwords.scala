package org.fiume.sketch.storage.auth0

import cats.Show
import cats.effect.Sync
import cats.implicits.*
import org.mindrot.jbcrypt.BCrypt

import java.util.Base64

object Passwords:

  case class PlainPassword(value: String) extends AnyVal

  sealed abstract case class Salt(base64Value: String)
  object Salt:
    val logRounds = 12

    def generate[F[_]]()(using F: Sync[F]): F[Salt] = F.delay { BCrypt.gensalt(logRounds) }.map(Salt.unsafeFromString)

    def unsafeFromString(base64: String): Salt = new Salt(base64) {}

    given Show[Salt] = Show.fromToString

  case class HashedPassword(base64Value: String) extends AnyVal:
    override def toString(): String = "********"

  object HashedPassword:
    def hashPassword(password: PlainPassword, salt: Salt): HashedPassword =
      val hashedPassword = BCrypt.hashpw(password.value, salt.base64Value)
      HashedPassword(hashedPassword)

    def verifyPassword(password: PlainPassword, hashedPassword: HashedPassword): Boolean =
      BCrypt.checkpw(password.value, hashedPassword.base64Value)

    given Show[HashedPassword] = Show.show(_ => "********")
