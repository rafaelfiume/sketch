package org.fiume.sketch.shared.auth0.algebras

import cats.Monad
import cats.implicits.*
import org.fiume.sketch.shared.app.algebras.Store
import org.fiume.sketch.shared.auth0.algebras.UsersStore.ActivateAccountError
import org.fiume.sketch.shared.auth0.algebras.UsersStore.ActivateAccountError.{AccountAlreadyActive, AccountNotFound}
import org.fiume.sketch.shared.auth0.domain.{Account, User, UserId}
import org.fiume.sketch.shared.auth0.domain.Passwords.HashedPassword
import org.fiume.sketch.shared.auth0.domain.User.*
import org.fiume.sketch.shared.auth0.jobs.ScheduledAccountDeletion

import java.time.Instant
import scala.concurrent.duration.Duration

trait UsersStore[F[_], Txn[_]: Monad] extends Store[F, Txn]:
  def createAccount(credentials: UserCredentials): Txn[UserId]

  def fetchAccount(userId: UserId): Txn[Option[Account]]

  def fetchAccountWith[T](userId: UserId)(f: Option[Account] => T): Txn[T] = fetchAccount(userId).map(f)

  def fetchAccount(username: Username): Txn[Option[Account]]

  def updatePassword(userId: UserId, password: HashedPassword): Txn[Unit]

  def delete(userId: UserId): Txn[Unit]

  // TODO Make it return Either[MarkForDeletionError, ScheduledAccountDeletion]
  def markForDeletion(userId: UserId, timeUntilPermanentDeletion: Duration): Txn[ScheduledAccountDeletion] =
    // TODO Validate transition
    softDeleteAccount(userId).flatMap { deletedAt =>
      val permanentDeletionAt = deletedAt.plusSeconds(timeUntilPermanentDeletion.toSeconds)
      schedulePermanentDeletion(userId, permanentDeletionAt)
    }

  def restoreAccount(userId: UserId): Txn[Either[ActivateAccountError, Account]] =
    val accountMarkedForDeletion = fetchAccountWith(userId) { _.fold(AccountNotFound.asLeft)(validateMarkedForDeletion(_)) }
    accountMarkedForDeletion.flatMap {
      case Right(account) =>
        activateAccount(account.uuid)
          .flatTap { _ => unschedulePermanentDeletion(account.uuid) }
          .as(account.asRight)
      case error => error.pure[Txn]
    }

  def claimNextJob(): Txn[Option[ScheduledAccountDeletion]]

  protected def softDeleteAccount(userId: UserId): Txn[Instant]
  protected def activateAccount(userId: UserId): Txn[Unit]

  protected def schedulePermanentDeletion(userId: UserId, permanentDeletionAt: Instant): Txn[ScheduledAccountDeletion]
  protected def unschedulePermanentDeletion(userId: UserId): Txn[Unit]

  // TODO Rename: This is effectively checking if state machine can transition from MarkedForDeletion to Active
  private def validateMarkedForDeletion(account: Account): Either[ActivateAccountError, Account] =
    account match
      case account if account.isMarkedForDeletion => account.asRight[ActivateAccountError]
      case account if account.isActive            => AccountAlreadyActive.asLeft
      case account                                => ActivateAccountError.Other(reason = s"unexpected status of $account").asLeft

object UsersStore:
  trait ActivateAccountError

  object ActivateAccountError:
    case object AccountAlreadyActive extends ActivateAccountError
    case object AccountNotFound extends ActivateAccountError
    case class Other(reason: String) extends ActivateAccountError
