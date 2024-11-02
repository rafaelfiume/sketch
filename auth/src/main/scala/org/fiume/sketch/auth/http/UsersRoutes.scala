package org.fiume.sketch.auth.http

import cats.effect.{Concurrent, Sync}
import cats.implicits.*
import org.fiume.sketch.shared.account.management.http.model.AccountStateTransitionErrorSyntax.*
import org.fiume.sketch.shared.auth.algebras.UsersStore
import org.fiume.sketch.shared.auth.domain.{Account, ActivateAccountError, SoftDeleteAccountError, User, UserId}
import org.fiume.sketch.shared.auth.domain.ActivateAccountError.*
import org.fiume.sketch.shared.auth.domain.SoftDeleteAccountError.*
import org.fiume.sketch.shared.auth.http.model.Users.{UserIdVar, *}
import org.fiume.sketch.shared.auth.http.model.Users.json.given
import org.fiume.sketch.shared.auth.jobs.ScheduledAccountDeletion
import org.fiume.sketch.shared.authorisation.{AccessControl, AuthorisationError, ContextualRole}
import org.fiume.sketch.shared.authorisation.ContextualRole.Owner
import org.fiume.sketch.shared.authorisation.syntax.AuthorisationErrorSyntax.*
import org.fiume.sketch.shared.common.algebras.syntax.StoreSyntax.*
import org.fiume.sketch.shared.common.troubleshooting.ErrorInfo
import org.fiume.sketch.shared.common.troubleshooting.ErrorInfo.json.given
import org.http4s.{HttpRoutes, *}
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.Http4sDsl
import org.http4s.server.{AuthMiddleware, Router}

import scala.concurrent.duration.Duration

class UsersRoutes[F[_]: Concurrent, Txn[_]: Sync](
  authMiddleware: AuthMiddleware[F, User],
  accessControl: AccessControl[F, Txn],
  store: UsersStore[F, Txn],
  delayUntilPermanentDeletion: Duration
) extends Http4sDsl[F]:

  private val prefix = "/"

  // enable Store's syntax
  given UsersStore[F, Txn] = store

  def router(): HttpRoutes[F] = Router(prefix -> authMiddleware(authedRoutes))

  private val authedRoutes: AuthedRoutes[User, F] =
    AuthedRoutes.of {
      case DELETE -> Root / "users" / UserIdVar(uuid) as authed =>
        markAccountForDeletion(authed.uuid, uuid)
          .commit()
          .flatMap {
            case Right(job) => Ok(job.asResponsePayload)
            case Left(error: SoftDeleteAccountError) =>
              error match
                // The request conflicts with the current state of the account (state machine transition error).
                case AccountAlreadyPendingDeletion          => Conflict(error.toErrorInfo)
                case SoftDeleteAccountError.AccountNotFound => NotFound(error.toErrorInfo)
            case Left(error: AuthorisationError) => Forbidden(error.toErrorInfo)
          }

      case POST -> Root / "users" / UserIdVar(uuid) / "restore" as authed =>
        restoreAccount(authed.uuid, uuid)
          .commit()
          .flatMap {
            case Right(_) => NoContent()
            case Left(error: ActivateAccountError) =>
              error match
                case AccountAlreadyActive                 => Conflict(error.toActivateErrorInfo)
                case ActivateAccountError.AccountNotFound => NotFound(error.toActivateErrorInfo)
            case Left(error: AuthorisationError) => Forbidden(error.toActivateErrorInfo)
          }
    }

  private def markAccountForDeletion(
    authedId: UserId,
    userId: UserId
  ): Txn[Either[AuthorisationError | SoftDeleteAccountError, ScheduledAccountDeletion]] =
    canManageAccount(authedId, userId).ifM(
      ifTrue = accessControl
        .ensureRevoked(userId, userId) {
          store.markForDeletion(_, delayUntilPermanentDeletion).map(_.widenErrorType)
        },
      ifFalse = AuthorisationError.UnauthorisedError.asLeft.pure[Txn]
    )

  private def restoreAccount(
    authedId: UserId,
    userToBeRestoredId: UserId
  ): Txn[Either[AuthorisationError | ActivateAccountError, Account]] =
    canManageAccount(authedId, userToBeRestoredId).ifM(
      ifTrue = accessControl.ensureAccess(userToBeRestoredId, Owner) {
        store.restoreAccount(userToBeRestoredId).map(_.widenErrorType)
      },
      ifFalse = AuthorisationError.UnauthorisedError.asLeft.pure[Txn]
    )

  /*
   * An example of a custom `canAccess` fn.
   */
  private def canManageAccount(authedId: UserId, account: UserId): Txn[Boolean] =
    def isAuthenticatedAccountActive(uuid: UserId): Txn[Boolean] = store.fetchAccountWith(uuid) { _.fold(false)(_.isActive) }
    (
      isAuthenticatedAccountActive(authedId), // for when the user deactivates their own account
      accessControl.canAccess(authedId, account)
    ).mapN(_ && _)
