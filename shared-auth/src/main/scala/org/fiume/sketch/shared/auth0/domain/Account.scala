package org.fiume.sketch.shared.auth.domain

import cats.implicits.*
import org.fiume.sketch.shared.auth.domain.ActivateAccountError.*
import org.fiume.sketch.shared.auth.domain.SoftDeleteAccountError.AccountAlreadyDeleted
import org.fiume.sketch.shared.auth.domain.User.UserCredentials

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
  case Active(activatedAt: Instant)
  // case Deactivated(reason: String)            // For instance, too many failed login attempts
  case SoftDeleted(deletedAt: Instant)
  // case PendingVerification                    // User must verify their email or other requirements

object AccountState:
  def transitionToActive(account: Account, activatedAt: Instant): Either[ActivateAccountError, Account] =
    account.state match
      case AccountState.Active(_) => AccountAlreadyActive.asLeft
      case AccountState.SoftDeleted(_) =>
        account.copy(state = AccountState.Active(activatedAt)).asRight

  def transitionToSoftDelete(account: Account, deletedAt: Instant): Either[SoftDeleteAccountError, Account] =
    account.state match
      case AccountState.SoftDeleted(_) => AccountAlreadyDeleted.asLeft
      case _                           => account.copy(state = AccountState.SoftDeleted(deletedAt)).asRight

sealed trait AccountStateTransitionError

enum ActivateAccountError extends AccountStateTransitionError:
  case AccountAlreadyActive
  case AccountNotFound

enum SoftDeleteAccountError extends AccountStateTransitionError:
  case AccountAlreadyDeleted
  case AccountNotFound
