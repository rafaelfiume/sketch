package org.fiume.sketch.shared.auth0.algebras

import cats.Monad
import cats.implicits.*
import org.fiume.sketch.shared.app.algebras.Store
import org.fiume.sketch.shared.auth0.domain.{Account, AccountState, ActivateAccountError, SoftDeleteAccountError, User, UserId}
import org.fiume.sketch.shared.auth0.domain.Passwords.HashedPassword
import org.fiume.sketch.shared.auth0.domain.SoftDeleteAccountError.*
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

  def deleteAccount(userId: UserId): Txn[Unit]

  def markForDeletion(
    userId: UserId,
    timeUntilPermanentDeletion: Duration
  ): Txn[Either[SoftDeleteAccountError, ScheduledAccountDeletion]] =
    getNow().flatMap { now =>
      val permanentDeletionAt = now.plusSeconds(timeUntilPermanentDeletion.toSeconds)
      fetchAccountWith(userId) { _.fold(AccountNotFound.asLeft)(AccountState.transitionToSoftDelete(_, now)) }.flatMap {
        case Right(account) =>
          updateAccount(account).flatMap { _ =>
            schedulePermanentDeletion(userId, permanentDeletionAt).map(_.asRight)
          }
        case Left(error) => error.asLeft[ScheduledAccountDeletion].pure[Txn]
      }
    }

  def restoreAccount(userId: UserId): Txn[Either[ActivateAccountError, Account]] =
    getNow().flatMap { now =>
      fetchAccountWith(userId) { _.fold(ActivateAccountError.AccountNotFound.asLeft)(AccountState.transitionToActive(_, now)) }
        .flatMap {
          case Right(account) =>
            updateAccount(account)
              .flatTap { _ => unschedulePermanentDeletion(account.uuid) }
              .as(account.asRight)
          case error => error.pure[Txn]
        }
    }

  def claimNextJob(): Txn[Option[ScheduledAccountDeletion]]

  protected def updateAccount(account: Account): Txn[Unit]

  protected def schedulePermanentDeletion(userId: UserId, permanentDeletionAt: Instant): Txn[ScheduledAccountDeletion]

  protected def unschedulePermanentDeletion(userId: UserId): Txn[Unit]

  protected def getNow(): Txn[Instant]
