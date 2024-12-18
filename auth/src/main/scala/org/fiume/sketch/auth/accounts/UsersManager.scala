package org.fiume.sketch.auth.accounts

import cats.Monad
import cats.effect.Sync
import cats.implicits.*
import org.fiume.sketch.shared.auth.Passwords.{HashedPassword, PlainPassword, Salt}
import org.fiume.sketch.shared.auth.User.*
import org.fiume.sketch.shared.auth.UserId
import org.fiume.sketch.shared.auth.accounts.{Account, ActivateAccountError, SoftDeleteAccountError}
import org.fiume.sketch.shared.auth.accounts.jobs.AccountDeletionEvent
import org.fiume.sketch.shared.auth.algebras.UsersStore
import org.fiume.sketch.shared.authorisation.{AccessControl, AccessDenied, ContextualRole, GlobalRole}
import org.fiume.sketch.shared.authorisation.ContextualRole.Owner
import org.fiume.sketch.shared.authorisation.syntax.AccessDeniedSyntax.*
import org.fiume.sketch.shared.common.algebras.syntax.StoreSyntax.*

import scala.concurrent.duration.Duration

trait UsersManager[F[_]]:
  def createAccount(username: Username, password: PlainPassword, globalRole: Option[GlobalRole] = none): F[UserId]

  def markAccountForDeletion(
    markingForDeletion: UserId,
    toBeMarkedForDeletion: UserId
  ): F[Either[AccessDenied.type | SoftDeleteAccountError, AccountDeletionEvent.Scheduled]]

  def restoreAccount(
    restoringAccount: UserId,
    accountToBeRestored: UserId
  ): F[Either[AccessDenied.type | ActivateAccountError, Account]]

object UsersManager:
  def make[F[_]: Sync, Txn[_]: Monad](
    store: UsersStore[F, Txn],
    accessControl: AccessControl[F, Txn],
    delayUntilPermanentDeletion: Duration
  ): UsersManager[F] =
    // enable Store's syntax
    given UsersStore[F, Txn] = store

    new UsersManager[F]:
      /*
       * Careful: there is no access control to this function!
       * It's not clear such access control is necessary or not, however...
       * Its present incarnation is meant to be used through an internal admin account setup script.
       */
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

      override def markAccountForDeletion(
        markingForDeletion: UserId,
        toBeMarkedForDeletion: UserId
      ): F[Either[AccessDenied.type | SoftDeleteAccountError, AccountDeletionEvent.Scheduled]] =
        canManageAccount(markingForDeletion, toBeMarkedForDeletion)
          .ifM(
            ifTrue = accessControl
              .ensureRevoked(toBeMarkedForDeletion, toBeMarkedForDeletion) {
                store.markForDeletion(_, delayUntilPermanentDeletion).map(_.widenErrorType)
              },
            ifFalse = AccessDenied.asLeft.pure[Txn]
          )
          .commit()

      override def restoreAccount(
        restoringAccount: UserId,
        accountToBeRestored: UserId
      ): F[Either[AccessDenied.type | ActivateAccountError, Account]] =
        canManageAccount(restoringAccount, accountToBeRestored)
          .ifM(
            ifTrue = accessControl.ensureAccess(accountToBeRestored, Owner) {
              store.restoreAccount(accountToBeRestored).map(_.widenErrorType)
            },
            ifFalse = AccessDenied.asLeft.pure[Txn]
          )
          .commit()

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
