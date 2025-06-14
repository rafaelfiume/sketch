package org.fiume.sketch.shared.auth.accounts

import cats.implicits.*
import org.fiume.sketch.shared.auth.User.UserCredentials
import org.fiume.sketch.shared.auth.UserId
import org.fiume.sketch.shared.auth.accounts.ActivateAccountError.*
import org.fiume.sketch.shared.auth.accounts.SoftDeleteAccountError.AccountAlreadyPendingDeletion
import org.fiume.sketch.shared.common.WithUuid

import java.time.Instant

case class Account(
  uuid: UserId,
  credentials: UserCredentials,
  // email: Email, // possibly in the future, depending on requirements
  state: AccountState
) extends WithUuid[UserId]:
  def isActive: Boolean = state match
    case AccountState.Active(_) => true
    case _                      => false

  def isMarkedForDeletion: Boolean = state match
    case AccountState.SoftDeleted(_) => true
    case _                           => false

enum AccountState:
  case Active(since: Instant)
  // case Deactivated(reason: String)            // For instance, too many failed login attempts
  case SoftDeleted(at: Instant)
  // case PendingVerification                    // User must verify their email or other requirements

object AccountState:
  def transitionToActive(account: Account, activatedAt: Instant): Either[ActivateAccountError, Account] =
    account.state match
      case AccountState.Active(_)      => AccountAlreadyActive.asLeft
      case AccountState.SoftDeleted(_) =>
        account.copy(state = AccountState.Active(activatedAt)).asRight

  def transitionToSoftDelete(account: Account, deletedAt: Instant): Either[SoftDeleteAccountError, Account] =
    account.state match
      case AccountState.SoftDeleted(_) => AccountAlreadyPendingDeletion.asLeft
      case _                           => account.copy(state = AccountState.SoftDeleted(deletedAt)).asRight

sealed trait AccountStateTransitionError

enum ActivateAccountError extends AccountStateTransitionError:
  case AccountAlreadyActive
  case AccountNotFound

enum SoftDeleteAccountError extends AccountStateTransitionError:
  case AccountAlreadyPendingDeletion
  case AccountNotFound
