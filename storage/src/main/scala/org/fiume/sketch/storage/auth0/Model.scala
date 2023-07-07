package org.fiume.sketch.storage.auth0

import cats.Show
import cats.effect.Sync

import java.time.ZonedDateTime
import java.util.UUID

object Model:
  // bcrypt or Argon2 ?
  case class PasswordHash(value: String) extends AnyVal:
    override def toString(): String = "********"

  object Salt:
    // TODO: Salf of 32 bytes long
    def generate[F[_]: Sync](): F[Salt] = ???
    def unsafeFromString(value: String): Salt = new Salt(value) {}

  sealed abstract case class Salt(value: String):
    override def toString(): String = "********"

  case class Username(value: String) extends AnyVal
  case class FirstName(value: String) extends AnyVal
  case class LastName(value: String) extends AnyVal
  case class Name(first: FirstName, last: LastName)
  case class Email(value: String) extends AnyVal
  case class User(username: Username, name: Name, email: Email)

  case class UserCredentials(
    id: UUID,
    password: PasswordHash,
    salt: Salt,
    user: User,
    createdAt: ZonedDateTime,
    updatedAt: ZonedDateTime
  )

  given Show[UUID] = Show.fromToString
  given Show[PasswordHash] = Show.show(_ => "********")
  given Show[Salt] = Show.show(_ => "********")
  given Show[Username] = Show.fromToString
  given Show[FirstName] = Show.fromToString
  given Show[LastName] = Show.fromToString
  given Show[Name] = Show.fromToString
  given Show[Email] = Show.fromToString
  given Show[User] = Show.fromToString
  given Show[UserCredentials] = Show.fromToString
