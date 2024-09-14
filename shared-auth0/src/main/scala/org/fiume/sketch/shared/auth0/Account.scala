package org.fiume.sketch.shared.auth0

import org.fiume.sketch.shared.auth0.User.UserCredentials

import java.time.Instant

case class Account(
  uuid: UserId,
  credentials: UserCredentials,
  // email: Email, // possibly in the future, depending on requirements
  state: AccountState
):
  def isActive: Boolean = state match
    case AccountState.Active(_) => true
    case _                      => false
// TODO Redact UserCredentials toString to avoid leaking sensitive information

enum AccountState:
  case Active(createdAt: Instant)
  // case Deactivated(reason: String)            // For instance, too many failed login attempts
  case SoftDeleted(deletedAt: Instant)
  // case Deleted(deletedAt: Instant)
  // case PendingVerification                    // User must verify their email or other requirements
