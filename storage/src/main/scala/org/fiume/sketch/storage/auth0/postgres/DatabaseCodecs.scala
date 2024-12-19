package org.fiume.sketch.storage.auth.postgres

import doobie.{Meta, Read}
import doobie.postgres.implicits.*
import doobie.util.Write
import org.fiume.sketch.shared.auth.Passwords.{HashedPassword, Salt}
import org.fiume.sketch.shared.auth.User.*
import org.fiume.sketch.shared.auth.UserId
import org.fiume.sketch.shared.auth.accounts.{Account, AccountState}
import org.fiume.sketch.shared.common.events.EventId

import java.time.Instant
import java.util.UUID

private[storage] object DatabaseCodecs:

  given Meta[UserId] = Meta[UUID].timap(UserId(_))(_.value)

  given Meta[HashedPassword] = Meta[String].timap(HashedPassword.makeUnsafeFromString)(_.base64Value)

  given Meta[Salt] = Meta[String].timap(Salt.makeUnsafeFromString)(_.base64Value)

  given Meta[Username] = Meta[String].timap(Username.makeUnsafeFromString)(_.value)

  given Read[UserCredentialsWithId] =
    Read[(UserId, Username, HashedPassword, Salt)].map { case (uuid, username, password, salt) =>
      UserCredentials.make(uuid, username, password, salt)
    }

  // "There is no equivalent to Meta for bidirectional column vector mappings."
  // Source: https://typelevel.org/doobie/docs/12-Custom-Mappings.html
  given Read[AccountState] = Read[(String, Instant, Option[Instant])].map {
    case ("Active", activatedAt, _)              => AccountState.Active(activatedAt)
    case ("PendingDeletion", _, Some(deletedAt)) => AccountState.SoftDeleted(deletedAt)
    case other                                   => throw new IllegalStateException(s"Unexpected account state: $other")
  }
  given Write[AccountState] = Write[String].contramap {
    case AccountState.Active(_)      => "Active"
    case AccountState.SoftDeleted(_) => "PendingDeletion"
  }

  given Read[Account] = Read[(UserCredentialsWithId, AccountState)].map { case (creds, state) =>
    Account(creds.uuid, creds, state)
  }

  given Meta[EventId] = Meta[UUID].timap(EventId(_))(_.value)
