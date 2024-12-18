package org.fiume.sketch.shared.auth.algebras

import cats.Monad
import cats.effect.kernel.Clock
import cats.implicits.*
import org.fiume.sketch.shared.auth.{User, UserId}
import org.fiume.sketch.shared.auth.Passwords.HashedPassword
import org.fiume.sketch.shared.auth.User.*
import org.fiume.sketch.shared.auth.accounts.{Account, AccountState, ActivateAccountError, SoftDeleteAccountError}
import org.fiume.sketch.shared.auth.accounts.SoftDeleteAccountError.*
import org.fiume.sketch.shared.auth.accounts.jobs.AccountDeletionEvent
import org.fiume.sketch.shared.common.algebras.Store

import scala.concurrent.duration.Duration

trait UsersStore[F[_], Txn[_]: Monad](clock: Clock[F]) extends Store[F, Txn]:

  def createAccount(credentials: UserCredentials): Txn[UserId]

  def fetchAccount(userId: UserId): Txn[Option[Account]]

  def fetchAccountWith[T](userId: UserId)(f: Option[Account] => T): Txn[T] = fetchAccount(userId).map(f)

  def fetchAccount(username: Username): Txn[Option[Account]]

  def updatePassword(userId: UserId, password: HashedPassword): Txn[Unit]

  def markForDeletion(
    userId: UserId,
    timeUntilPermanentDeletion: Duration
  ): Txn[Either[SoftDeleteAccountError, AccountDeletionEvent.Scheduled]] =
    lift { clock.realTimeInstant }.flatMap { now =>
      val permanentDeletionAt = now.plusSeconds(timeUntilPermanentDeletion.toSeconds)
      fetchAccountWith(userId) { _.fold(AccountNotFound.asLeft)(AccountState.transitionToSoftDelete(_, now)) }.flatMap {
        case Right(account) =>
          updateAccount(account).flatMap { _ =>
            schedulePermanentDeletion(AccountDeletionEvent.Unscheduled(userId, permanentDeletionAt)).map(_.asRight)
          }
        case Left(error) => error.asLeft[AccountDeletionEvent.Scheduled].pure[Txn]
      }
    }

  def deleteAccount(userId: UserId): Txn[Unit]

  def restoreAccount(userId: UserId): Txn[Either[ActivateAccountError, Account]] =
    lift { clock.realTimeInstant }.flatMap { now =>
      fetchAccountWith(userId) { _.fold(ActivateAccountError.AccountNotFound.asLeft)(AccountState.transitionToActive(_, now)) }
        .flatMap {
          case Right(account) =>
            updateAccount(account)
              .flatTap { _ => unschedulePermanentDeletion(account.uuid) }
              .as(account.asRight)
          case error => error.pure[Txn]
        }
    }

  protected def updateAccount(account: Account): Txn[Unit]

  protected def schedulePermanentDeletion(event: AccountDeletionEvent.Unscheduled): Txn[AccountDeletionEvent.Scheduled]

  protected def unschedulePermanentDeletion(userId: UserId): Txn[Unit]
