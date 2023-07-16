package org.fiume.sketch.storage.auth0.postgres

import doobie.{Meta, Read}
import doobie.postgres.implicits.*
import org.fiume.sketch.shared.auth0.Model.*
import org.fiume.sketch.shared.auth0.Passwords.{HashedPassword, Salt}

import java.time.ZonedDateTime
import java.util.UUID

private[postgres] object DoobieMappings:

  given Meta[HashedPassword] = Meta[String].timap(HashedPassword.unsafeFromString)(_.base64Value)

  given Meta[Salt] = Meta[String].timap(Salt.unsafeFromString)(_.base64Value)

  given Meta[Username] = Meta[String].timap(Username.apply)(_.value)

  given Read[User] = Read[(UUID, Username)].map { case (uuid, username) => User(uuid, username) }
