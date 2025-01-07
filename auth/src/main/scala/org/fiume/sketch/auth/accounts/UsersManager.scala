package org.fiume.sketch.auth.accounts

import cats.Monad
import cats.effect.Sync
import cats.effect.kernel.Clock
import cats.implicits.*
import org.fiume.sketch.shared.auth.Passwords.{HashedPassword, PlainPassword, Salt}
import org.fiume.sketch.shared.auth.User.*
import org.fiume.sketch.shared.auth.UserId
import org.fiume.sketch.shared.auth.accounts.{
  Account,
  AccountDeletionEvent,
  AccountState,
  ActivateAccountError,
  SoftDeleteAccountError
}
import org.fiume.sketch.shared.auth.algebras.UsersStore
import org.fiume.sketch.shared.authorisation.{AccessControl, AccessDenied, ContextualRole, GlobalRole}
import org.fiume.sketch.shared.authorisation.ContextualRole.Owner
import org.fiume.sketch.shared.authorisation.syntax.AccessDeniedSyntax.*
import org.fiume.sketch.shared.common.app.syntax.StoreSyntax.*
import org.fiume.sketch.shared.common.events.CancellableEventProducer

import scala.concurrent.duration.Duration

trait UsersManager[F[_]]:
  def createAccount(username: Username, password: PlainPassword, globalRole: Option[GlobalRole] = none): F[UserId]

  def attemptToMarkAccountForDeletion(
    markingForDeletion: UserId,
    toBeMarkedForDeletion: UserId
  ): F[Either[AccessDenied.type | SoftDeleteAccountError, AccountDeletionEvent.Scheduled]]

  def attemptToRestoreAccount(
    restoringAccount: UserId,
    accountToBeRestored: UserId
  ): F[Either[AccessDenied.type | ActivateAccountError, Account]]

  def markForDeletion(
    userId: UserId,
    timeUntilPermanentDeletion: Duration
  ): F[Either[SoftDeleteAccountError, AccountDeletionEvent.Scheduled]]

  def restoreAccount(userId: UserId): F[Either[ActivateAccountError, Account]]

object UsersManager:
  def make[F[_]: Sync, Txn[_]: Monad](
    store: UsersStore[F, Txn],
    producer: CancellableEventProducer[Txn, AccountDeletionEvent.ToSchedule, UserId],
    accessControl: AccessControl[F, Txn],
    clock: Clock[F],
    delayUntilPermanentDeletion: Duration
  ): UsersManager[F] =
    // enable Store's syntax
    given UsersStore[F, Txn] = store

    new UsersManager[F]:
      override def createAccount(username: Username, password: PlainPassword, globalRole: Option[GlobalRole]): F[UserId] =
        val credentials = for
          salt <- Salt.generate()
          hashedPassword <- HashedPassword.hashPassword(password, salt)
        yield UserCredentials(username, hashedPassword, salt)

        val setUpAccount = for
          creds <- store.lift { credentials }
          userId <- store.createAccount(creds)
          _ <- accessControl.grantAccess(userId, userId, ContextualRole.Owner)
          _ <- globalRole.fold(ifEmpty = ().pure[Txn])(accessControl.grantGlobalAccess(userId, _))
        yield userId

        setUpAccount.commit()

      override def attemptToMarkAccountForDeletion(
        markingForDeletion: UserId,
        toBeMarkedForDeletion: UserId
      ): F[Either[AccessDenied.type | SoftDeleteAccountError, AccountDeletionEvent.Scheduled]] =
        canManageAccount(markingForDeletion, toBeMarkedForDeletion)
          .ifM(
            ifTrue = accessControl
              .ensureRevoked(toBeMarkedForDeletion, toBeMarkedForDeletion) {
                doMarkForDeletion(_, delayUntilPermanentDeletion).map(_.widenErrorType)
              },
            ifFalse = AccessDenied.asLeft.pure[Txn]
          )
          .commit()

      override def attemptToRestoreAccount(
        restoringAccount: UserId,
        accountToBeRestored: UserId
      ): F[Either[AccessDenied.type | ActivateAccountError, Account]] =
        canManageAccount(restoringAccount, accountToBeRestored)
          .ifM(
            ifTrue = accessControl.ensureAccess(accountToBeRestored, Owner) {
              doRestoreAccount(accountToBeRestored).map(_.widenErrorType)
            },
            ifFalse = AccessDenied.asLeft.pure[Txn]
          )
          .commit()

      // commits the transaction... for now
      override def markForDeletion(
        userId: UserId,
        timeUntilPermanentDeletion: Duration
      ): F[Either[SoftDeleteAccountError, AccountDeletionEvent.Scheduled]] =
        doMarkForDeletion(userId, timeUntilPermanentDeletion).commit()

      private def doMarkForDeletion(
        userId: UserId,
        timeUntilPermanentDeletion: Duration
      ): Txn[Either[SoftDeleteAccountError, AccountDeletionEvent.Scheduled]] =
        store.lift { clock.realTimeInstant }.flatMap { now =>
          val permanentDeletionAt = now.plusSeconds(timeUntilPermanentDeletion.toSeconds)
          store
            .fetchAccountWith(userId) {
              _.fold(SoftDeleteAccountError.AccountNotFound.asLeft)(AccountState.transitionToSoftDelete(_, now))
            }
            .flatMap {
              case Right(account) =>
                store.updateAccount(account).flatMap { _ =>
                  producer.produceEvent(AccountDeletionEvent.ToSchedule(userId, permanentDeletionAt)).map(_.asRight)
                }
              case Left(error) => error.asLeft[AccountDeletionEvent.Scheduled].pure[Txn]
            }
        }

      // commits the transaction... for now
      def restoreAccount(userId: UserId): F[Either[ActivateAccountError, Account]] =
        doRestoreAccount(userId).commit()

      private def doRestoreAccount(userId: UserId): Txn[Either[ActivateAccountError, Account]] =
        store.lift { clock.realTimeInstant }.flatMap { now =>
          store
            .fetchAccountWith(userId) {
              _.fold(ActivateAccountError.AccountNotFound.asLeft)(AccountState.transitionToActive(_, now))
            }
            .flatMap {
              case Right(account) =>
                store
                  .updateAccount(account)
                  .flatTap { _ => producer.cancelEvent(account.uuid) }
                  .as(account.asRight)
              case error => error.pure[Txn]
            }
        }

      /*
       * An example of a custom `canAccess` fn.
       */
      private def canManageAccount(authedId: UserId, account: UserId): Txn[Boolean] =
        def isAuthenticatedAccountActive(uuid: UserId): Txn[Boolean] = store.fetchAccountWith(uuid) {
          _.fold(false)(_.isActive)
        }
        (
          isAuthenticatedAccountActive(authedId), // for when the user deactivates their own account
          accessControl.canAccess(authedId, account)
        ).mapN(_ && _)
