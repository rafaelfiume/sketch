package org.fiume.sketch.shared.auth0

import cats.Show
import org.fiume.sketch.shared.auth0.Passwords.{HashedPassword, Salt}

import java.util.UUID

object Model:
  case class Username(value: String) extends AnyVal

  case class User(
    id: UUID,
    username: Username
  )

  given Show[UUID] = Show.fromToString
  given Show[Username] = Show.fromToString
  given Show[User] = Show.fromToString
