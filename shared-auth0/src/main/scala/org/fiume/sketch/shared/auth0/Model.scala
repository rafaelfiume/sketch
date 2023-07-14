package org.fiume.sketch.shared.auth0

import cats.Show
import org.fiume.sketch.shared.auth0.Passwords.{HashedPassword, Salt}

import java.time.ZonedDateTime
import java.util.UUID

object Model:
  case class Username(value: String) extends AnyVal
  case class FirstName(value: String) extends AnyVal
  case class LastName(value: String) extends AnyVal
  case class Name(first: FirstName, last: LastName)
  case class Email(value: String) extends AnyVal
  case class User(username: Username, name: Name, email: Email)

  case class UserCredentials(
    id: UUID,
    password: HashedPassword,
    salt: Salt,
    user: User,
    createdAt: ZonedDateTime,
    updatedAt: ZonedDateTime
  )

  given Show[UUID] = Show.fromToString
  given Show[Username] = Show.fromToString
  given Show[FirstName] = Show.fromToString
  given Show[LastName] = Show.fromToString
  given Show[Name] = Show.fromToString
  given Show[Email] = Show.fromToString
  given Show[User] = Show.fromToString
  given Show[UserCredentials] = Show.fromToString
