package org.fiume.sketch.shared.auth0.domain

import cats.implicits.*
import org.fiume.sketch.shared.auth0.domain.ActivateAccountError.*
import org.fiume.sketch.shared.auth0.domain.SoftDeleteAccountError.AccountAlreadyDeleted
import org.fiume.sketch.shared.auth0.domain.User.UserCredentials

import java.time.Instant
import scala.util.control.NoStackTrace

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
  // TODO Have a think on the name of these functions
  def transitionToActive(account: Account, activatedAt: Instant): Either[ActivateAccountError, Account] =
    account.state match
      case AccountState.Active(_) => AccountAlreadyActive.asLeft
      case AccountState.SoftDeleted(_) =>
        account.copy(state = AccountState.Active(activatedAt)).asRight

  def transitionToSoftDelete(account: Account, deletedAt: Instant): Either[SoftDeleteAccountError, Account] =
    account.state match
      case AccountState.SoftDeleted(_) => AccountAlreadyDeleted.asLeft
      case _                           => account.copy(state = AccountState.SoftDeleted(deletedAt)).asRight

sealed trait AccountStateTransitionError extends Throwable with NoStackTrace

sealed trait ActivateAccountError extends AccountStateTransitionError
object ActivateAccountError:
  case object AccountAlreadyActive extends ActivateAccountError
  case object AccountNotFound extends ActivateAccountError
  case object Other extends ActivateAccountError

sealed trait SoftDeleteAccountError extends AccountStateTransitionError
object SoftDeleteAccountError:
  case object AccountAlreadyDeleted extends SoftDeleteAccountError
  case object AccountNotFound extends SoftDeleteAccountError
  case object Other extends SoftDeleteAccountError
