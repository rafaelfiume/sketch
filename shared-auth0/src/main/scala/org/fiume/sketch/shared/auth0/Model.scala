package org.fiume.sketch.shared.auth0

import cats.Show
import org.fiume.sketch.shared.auth0.Passwords.{HashedPassword, Salt}

import java.util.UUID

object Model:
  // TODO Define rules for username creation
  case class Username(value: String) extends AnyVal

  case class User(
    uuid: UUID,
    username: Username
  )

  // set of information required to authenticate a user
  case class Credentials(
    uuid: UUID,
    username: Username,
    hashedPassword: HashedPassword
    // salt: Salt
  )

  given Show[UUID] = Show.fromToString
  given Show[Username] = Show.fromToString
  given Show[User] = Show.fromToString
