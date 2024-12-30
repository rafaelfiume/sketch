package org.fiume.sketch.auth.http

import cats.effect.{Concurrent, Sync}
import cats.implicits.*
import org.fiume.sketch.auth.accounts.UsersManager
import org.fiume.sketch.shared.account.management.http.model.AccountStateTransitionErrorSyntax.*
import org.fiume.sketch.shared.auth.User
import org.fiume.sketch.shared.auth.accounts.{Account, ActivateAccountError, SoftDeleteAccountError}
import org.fiume.sketch.shared.auth.accounts.ActivateAccountError.*
import org.fiume.sketch.shared.auth.accounts.SoftDeleteAccountError.*
import org.fiume.sketch.shared.auth.http.model.Users.{UserIdVar, *}
import org.fiume.sketch.shared.auth.http.model.Users.json.given
import org.fiume.sketch.shared.authorisation.AccessDenied
import org.fiume.sketch.shared.common.troubleshooting.ErrorInfo
import org.fiume.sketch.shared.common.troubleshooting.ErrorInfo.json.given
import org.http4s.{HttpRoutes, *}
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.Http4sDsl
import org.http4s.server.{AuthMiddleware, Router}

class UsersRoutes[F[_]: Concurrent, Txn[_]: Sync](
  authMiddleware: AuthMiddleware[F, User],
  usersManager: UsersManager[F]
) extends Http4sDsl[F]:

  private val prefix = "/"

  def router(): HttpRoutes[F] = Router(prefix -> authMiddleware(authedRoutes))

  private val authedRoutes: AuthedRoutes[User, F] =
    AuthedRoutes.of {
      case DELETE -> Root / "users" / UserIdVar(uuid) as authed =>
        usersManager
          .attemptToMarkAccountForDeletion(authed.uuid, uuid)
          .flatMap {
            case Right(job) => Ok(job.asResponsePayload)
            case Left(error: SoftDeleteAccountError) =>
              error match
                // The request conflicts with the current state of the account (state machine transition error).
                case AccountAlreadyPendingDeletion          => Conflict(error.toErrorInfo)
                case SoftDeleteAccountError.AccountNotFound => NotFound(error.toErrorInfo)
            case Left(error: AccessDenied.type) => Forbidden(error.toErrorInfo)
          }

      case POST -> Root / "users" / UserIdVar(uuid) / "restore" as authed =>
        usersManager
          .attemptToRestoreAccount(authed.uuid, uuid)
          .flatMap {
            case Right(_) => NoContent()
            case Left(error: ActivateAccountError) =>
              error match
                case AccountAlreadyActive                 => Conflict(error.toActivateErrorInfo)
                case ActivateAccountError.AccountNotFound => NotFound(error.toActivateErrorInfo)
            case Left(error: AccessDenied.type) => Forbidden(error.toActivateErrorInfo)
          }
    }
