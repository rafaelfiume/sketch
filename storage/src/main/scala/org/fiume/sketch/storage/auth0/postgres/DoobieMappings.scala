package org.fiume.sketch.storage.auth0.postgres

import doobie.{Meta, Read}
import doobie.postgres.implicits.*
import doobie.util.Write
import org.fiume.sketch.shared.auth0.{Account, AccountState, User, UserId}
import org.fiume.sketch.shared.auth0.Passwords.{HashedPassword, Salt}
import org.fiume.sketch.shared.auth0.User.*

import java.time.Instant
import java.util.UUID

private[storage] object DoobieMappings:

  given Meta[HashedPassword] = Meta[String].timap(HashedPassword.notValidatedFromString)(_.base64Value)

  given Meta[Salt] = Meta[String].timap(Salt.notValidatedFromString)(_.base64Value)

  given Meta[Username] = Meta[String].timap(Username.notValidatedFromString)(_.value)

  given Meta[UserId] = Meta[UUID].timap(UserId(_))(_.value)

  given Read[UserCredentialsWithId] =
    Read[(UserId, Username, HashedPassword, Salt)].map { case (uuid, username, password, salt) =>
      UserCredentials.make(uuid, username, password, salt)
    }

  // "There is no equivalent to Meta for bidirectional column vector mappings."
  // Source: https://typelevel.org/doobie/docs/12-Custom-Mappings.html
  given Read[AccountState] = Read[(String, Instant)].map {
    case ("Active", createdAt) => AccountState.Active(createdAt)
    // TODO Error handling?
    case _                     => ???
  }
  given Write[AccountState] = Write[(String, Instant)].contramap {
    case AccountState.Active(createdAt) => ("Active", createdAt)
  }

  given Read[Account] = Read[(UserCredentialsWithId, AccountState)].map { case (creds, state) =>
    Account(creds.uuid, creds, state)
  }
