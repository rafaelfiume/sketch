package org.fiume.sketch.storage.auth0.postgres

import doobie.{Meta, Read}
import doobie.postgres.implicits.*
import org.fiume.sketch.storage.auth0.Model.*

import java.time.ZonedDateTime
import java.util.UUID

private[postgres] object DoobieMappings:

  given Read[UUID] = Read[String].map(UUID.fromString)

  given Meta[PasswordHash] = Meta[String].timap(PasswordHash.apply)(_.value)

  given Meta[Salt] = Meta[String].timap(Salt.apply)(_.value)

  given Meta[Username] = Meta[String].timap(Username.apply)(_.value)

  given Meta[FirstName] = Meta[String].timap(FirstName.apply)(_.value)

  given Meta[LastName] = Meta[String].timap(LastName.apply)(_.value)

  given Meta[Email] = Meta[String].timap(Email.apply)(_.value)

  given Read[UserCredentials] =
    Read[(UUID, PasswordHash, Salt, Username, FirstName, LastName, Email, ZonedDateTime, ZonedDateTime)]
      .map { case (id, password, salt, username, first, last, email, createdAt, updatedAt) =>
        UserCredentials(id, password, salt, User(username, Name(first, last), email), createdAt, updatedAt)
      }