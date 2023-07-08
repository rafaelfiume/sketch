package org.fiume.sketch.storage.auth0

import cats.Show
import org.fiume.sketch.storage.auth0.Salt
import org.mindrot.jbcrypt.BCrypt

case class HashedPassword(value: String) extends AnyVal:
  override def toString(): String = "********"

object HashedPassword:
  def hashPassword(password: String, salt: Salt): HashedPassword =
    val hashedPassword = BCrypt.hashpw(password, salt.base64Value)
    HashedPassword(hashedPassword)

  def verifyPassword(password: String, hashedPassword: HashedPassword): Boolean =
    BCrypt.checkpw(password, hashedPassword.value)

  given Show[HashedPassword] = Show.show(_ => "********")
