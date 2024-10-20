package org.fiume.sketch.auth.http

import cats.effect.{Concurrent, Sync}
import cats.implicits.*
import io.circe.{Decoder, Encoder}
import org.fiume.sketch.auth.http.UsersRoutes.Model.asResponsePayload
import org.fiume.sketch.auth.http.UsersRoutes.Model.json.given
import org.fiume.sketch.auth.http.UsersRoutes.UserIdVar
import org.fiume.sketch.shared.auth.algebras.UsersStore
import org.fiume.sketch.shared.auth.domain.{Account, ActivateAccountError, SoftDeleteAccountError, User, UserId}
import org.fiume.sketch.shared.auth.domain.ActivateAccountError.*
import org.fiume.sketch.shared.auth.jobs.ScheduledAccountDeletion
import org.fiume.sketch.shared.authorisation.{AccessControl, AuthorisationError, ContextualRole}
import org.fiume.sketch.shared.authorisation.ContextualRole.Owner
import org.fiume.sketch.shared.common.algebras.syntax.StoreSyntax.*
import org.fiume.sketch.shared.common.http.json.JsonCodecs.given
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
        accessControl
          .attemptAccountManagementWithAuthorisation(authed.uuid, uuid, isAuthenticatedAccountActive)(doMarkForDeletion)
          .commit()
          .flatMap {
            case Right(job) => Ok(job.asResponsePayload)
            case _          => Forbidden()
          }

      case PUT -> Root / "users" / UserIdVar(uuid) / "restore" as authed =>
        accessControl
          .attemptAccountManagementWithAuthorisation(authed.uuid, uuid, isAuthenticatedAccountActive)(doRestoreAccount)
          .commit()
          .flatMap {
            case Right(_)                   => NoContent()
            case Left(AccountAlreadyActive) => NoContent()
            case _                          => Forbidden()
          }
    }

  private def isAuthenticatedAccountActive(uuid: UserId): Txn[Boolean] = store.fetchAccountWith(uuid) {
    _.fold(false)(_.isActive)
  }

  private def doMarkForDeletion(userId: UserId): Txn[Either[SoftDeleteAccountError, ScheduledAccountDeletion]] =
    store.markForDeletion(userId, delayUntilPermanentDeletion).flatTap { outcome =>
      accessControl.revokeContextualAccess(userId, userId).whenA(outcome.isRight)
    }

  private def doRestoreAccount(userToBeRestoredId: UserId): Txn[Either[ActivateAccountError, Account]] =
    accessControl.ensureAccess(userToBeRestoredId, Owner)(store.restoreAccount(userToBeRestoredId))

private[http] object UsersRoutes:
  object UserIdVar:

    import org.fiume.sketch.shared.auth.domain.UserId.given
    def unapply(uuid: String): Option[UserId] = uuid.parsed().toOption

  object Model:
    case class ScheduledForPermanentDeletionResponse(userId: UserId, permanentDeletionAt: Instant)

    extension (job: ScheduledAccountDeletion)
      def asResponsePayload: ScheduledForPermanentDeletionResponse =
        ScheduledForPermanentDeletionResponse(job.userId, job.permanentDeletionAt)

    object json:
      import io.circe.generic.semiauto.*
      given Decoder[UserId] = Decoder.decodeUUID.map(UserId(_))
      given Encoder[ScheduledForPermanentDeletionResponse] = deriveEncoder
      given Decoder[ScheduledForPermanentDeletionResponse] = deriveDecoder
