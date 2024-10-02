package org.fiume.sketch.shared.auth0.domain

import org.fiume.sketch.shared.auth0.domain.User.UserCredentials

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

  def isMarkedForDeletion: Boolean = state match
    case AccountState.SoftDeleted(_) => true
    case _                           => false

enum AccountState:
  case Active(createdAt: Instant)
  // case Deactivated(reason: String)            // For instance, too many failed login attempts
  case SoftDeleted(deletedAt: Instant)
  // case PendingVerification                    // User must verify their email or other requirements
