package org.fiume.sketch.auth.http

import cats.effect.{Concurrent, Sync}
import cats.implicits.*
import io.circe.{Decoder, Encoder}
import org.fiume.sketch.auth.http.UsersRoutes.*
import org.fiume.sketch.auth.http.UsersRoutes.model.Error.*
import org.fiume.sketch.auth.http.UsersRoutes.model.asResponsePayload
import org.fiume.sketch.auth.http.UsersRoutes.model.json.given
import org.fiume.sketch.shared.auth.algebras.UsersStore
import org.fiume.sketch.shared.auth.domain.{Account, ActivateAccountError, SoftDeleteAccountError, User, UserEntity, UserId}
import org.fiume.sketch.shared.auth.domain.ActivateAccountError.*
import org.fiume.sketch.shared.auth.domain.SoftDeleteAccountError.*
import org.fiume.sketch.shared.auth.jobs.ScheduledAccountDeletion
import org.fiume.sketch.shared.authorisation.{AccessControl, AuthorisationError, ContextualRole}
import org.fiume.sketch.shared.authorisation.AuthorisationError.UnauthorisedError
import org.fiume.sketch.shared.authorisation.ContextualRole.Owner
import org.fiume.sketch.shared.common.algebras.syntax.StoreSyntax.*
import org.fiume.sketch.shared.common.http.json.JsonCodecs.given
import org.fiume.sketch.shared.common.troubleshooting.ErrorInfo
import org.fiume.sketch.shared.common.troubleshooting.ErrorInfo.{ErrorCode, ErrorMessage}
import org.fiume.sketch.shared.common.troubleshooting.ErrorInfo.json.given
import org.http4s.{HttpRoutes, *}
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.Http4sDsl
import org.http4s.server.{AuthMiddleware, Router}

import java.time.Instant
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

  /*
   * This is an interesting case where it is necessary to customise both `canAccess` function
   * and how `revokeContextualAccess` is invoked.
   */
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

private[http] object UsersRoutes:
  object UserIdVar:
    import org.fiume.sketch.shared.auth.domain.UserId.given
    def unapply(uuid: String): Option[UserId] = uuid.parsed().toOption

  // TODO Move this extension fn to `access-control` module?
  extension [E, R](result: Either[E, R]) def widenErrorType = result.leftMap[AuthorisationError | E](identity)

  object model:
    object Error:
      extension (error: SoftDeleteAccountError | AuthorisationError)
        def toErrorInfo =
          val (errorCode, errorMessage) = error match
            case AccountAlreadyPendingDeletion => ErrorCode("1200") -> ErrorMessage("Account already marked for deletion")
            case SoftDeleteAccountError.AccountNotFound => ErrorCode("1201") -> ErrorMessage("Account not found")
            case UnauthorisedError                      => ErrorCode("3000") -> ErrorMessage("Unauthorised operation")
          ErrorInfo.make(errorCode, errorMessage)

      extension (error: ActivateAccountError | AuthorisationError)
        def toActivateErrorInfo =
          val (errorCode, errorMessage) = error match
            case AccountAlreadyActive                 => ErrorCode("1210") -> ErrorMessage("Account is already active")
            case ActivateAccountError.AccountNotFound => ErrorCode("1211") -> ErrorMessage("Account not found")
            case UnauthorisedError                    => ErrorCode("3000") -> ErrorMessage("Unauthorised operation")
          ErrorInfo.make(errorCode, errorMessage)

    case class ScheduledForPermanentDeletionResponse(userId: UserId, permanentDeletionAt: Instant)

    extension (job: ScheduledAccountDeletion)
      def asResponsePayload: ScheduledForPermanentDeletionResponse =
        ScheduledForPermanentDeletionResponse(job.userId, job.permanentDeletionAt)

    object json:
      import io.circe.generic.semiauto.*
      given Decoder[UserId] = Decoder.decodeUUID.map(UserId(_))
      given Encoder[ScheduledForPermanentDeletionResponse] = deriveEncoder
      given Decoder[ScheduledForPermanentDeletionResponse] = deriveDecoder
