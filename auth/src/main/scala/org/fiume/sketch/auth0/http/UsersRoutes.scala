package org.fiume.sketch.auth.http

import cats.effect.{Concurrent, Sync}
import cats.implicits.*
import io.circe.{Decoder, Encoder}
import org.fiume.sketch.auth.http.UsersRoutes.Model.asResponsePayload
import org.fiume.sketch.auth.http.UsersRoutes.Model.json.given
import org.fiume.sketch.auth.http.UsersRoutes.UserIdVar
import org.fiume.sketch.shared.auth.algebras.UsersStore
import org.fiume.sketch.shared.auth.domain.{Account, AccountStateTransitionError, ActivateAccountError, SoftDeleteAccountError, User, UserId}
import org.fiume.sketch.shared.auth.domain.ActivateAccountError.*
import org.fiume.sketch.shared.auth.jobs.ScheduledAccountDeletion
import org.fiume.sketch.shared.authorisation.{AccessControl, AuthorisationError}
import org.fiume.sketch.shared.authorisation.AuthorisationError.*
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
        canAuthedUserManageAccount(authed.uuid, uuid)(doMarkForDeletion)
          .commit()
          .flatMap {
            case Right(job) => Ok(job.asResponsePayload)
            case _          => Forbidden()
          }

      case PUT -> Root / "users" / UserIdVar(uuid) / "restore" as authed =>
        canAuthedUserManageAccount(authed.uuid, uuid)(doRestoreAccount)
          .commit()
          .flatMap {
            case Right(_)                   => NoContent()
            case Left(AccountAlreadyActive) => NoContent()
            case _                          => Forbidden()
          }
    }

  // TODO Move it to AccessControl?
  private def canAuthedUserManageAccount[E <: AccountStateTransitionError, R](authenticated: UserId, account: UserId)(
    changeAccountIfAuthorised: UserId => Txn[Either[E, R]]
  ): Txn[Either[E | AuthorisationError, R]] =
    def isActiveAccount(uuid: UserId) = store.fetchAccountWith(uuid) { _.fold(false)(_.isActive) }
    (
      isActiveAccount(authenticated), // for when the user deactivates his own account
      accessControl.canAccess(authenticated, account)
    ).mapN(_ && _)
      .ifM(
        ifTrue = changeAccountIfAuthorised(account).map { _.leftMap[E | AuthorisationError](identity) }, // widening the left type
        ifFalse = UnauthorisedError.asLeft.pure[Txn]
      )

  private def doMarkForDeletion(userId: UserId): Txn[Either[SoftDeleteAccountError, ScheduledAccountDeletion]] =
    store.markForDeletion(userId, delayUntilPermanentDeletion).flatTap { outcome =>
      accessControl.revokeContextualAccess(userId, userId).whenA(outcome.isRight)
    }

  private def doRestoreAccount(accountToBeRestoredId: UserId): Txn[Either[ActivateAccountError, Account]] =
    store.restoreAccount(accountToBeRestoredId).flatTap { outcome =>
      accessControl.grantAccess(accountToBeRestoredId, accountToBeRestoredId, Owner).whenA(outcome.isRight)
    }

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
