package org.fiume.sketch.storage.auth0.postgres

import doobie.{Meta, Read}
import doobie.postgres.implicits.*
import org.fiume.sketch.shared.auth0.{User, UserUuid}
import org.fiume.sketch.shared.auth0.Passwords.{HashedPassword, Salt}
import org.fiume.sketch.shared.auth0.User.*

private[postgres] object DoobieMappings:

  given Meta[HashedPassword] = Meta[String].timap(HashedPassword.notValidatedFromString)(_.base64Value)

  given Meta[Salt] = Meta[String].timap(Salt.notValidatedFromString)(_.base64Value)

  given Meta[Username] = Meta[String].timap(Username.notValidatedFromString)(_.value)

  given Read[User] = Read[(UserUuid, Username)].map { case (uuid, username) => User(uuid, username) }

  given Read[UserCredentialsWithId] =
    Read[(UserUuid, Username, HashedPassword, Salt)].map { case (uuid, username, password, salt) =>
      UserCredentials.withUuid(uuid, username, password, salt)
    }
