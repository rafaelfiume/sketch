package org.fiume.sketch.auth0.http

import cats.effect.{Concurrent, Sync}
import cats.implicits.*
import io.circe.{Encoder, Json}
import io.circe.syntax.*
import org.fiume.sketch.auth0.http.UsersRoutes.Model.asResponsePayload
import org.fiume.sketch.auth0.http.UsersRoutes.Model.json.given
import org.fiume.sketch.auth0.http.UsersRoutes.UserIdVar
import org.fiume.sketch.authorisation.AccessControl
import org.fiume.sketch.shared.app.EntityId.given
import org.fiume.sketch.shared.app.algebras.Store.syntax.*
import org.fiume.sketch.shared.app.http4s.JsonCodecs.given
import org.fiume.sketch.shared.auth0.algebras.UsersStore
import org.fiume.sketch.shared.auth0.algebras.UsersStore.ActivateAccountError
import org.fiume.sketch.shared.auth0.algebras.UsersStore.ActivateAccountError.AccountAlreadyActive
import org.fiume.sketch.shared.auth0.domain.{Account, User, UserId}
import org.fiume.sketch.shared.auth0.jobs.ScheduledAccountDeletion
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
        for
          outcome <- canAuthedUserMarkAccountForDeletion(authed, uuid)
            .ifM(
              ifTrue = doMarkForDeletion(uuid).map(_.asResponsePayload).map(_.some),
              ifFalse = none.pure[Txn]
            )
            .commit()
          res <- outcome.fold(Forbidden())(Ok(_))
        yield res

      case PUT -> Root / "users" / UserIdVar(uuid) / "restore" as authed =>
        val outcome =
          for outcome <- canAccountBeRestored(authed, uuid)
              .ifM(
                ifTrue = store.restoreAccount(uuid),
                ifFalse = Left(ActivateAccountError.Other(reason = "Unauthorised")).pure[Txn]
              )
          yield outcome
        outcome.commit().flatMap {
          case Right(_)                                         => NoContent()
          case Left(AccountAlreadyActive)                       => NoContent()
          case Left(ActivateAccountError.Other("Unauthorised")) => Forbidden()
          case Left(error)                                      => InternalServerError(error.toString)
        }
      // TODO Remove scheduled job (make sure job validates state before deletion)
    }

  private def canAuthedUserMarkAccountForDeletion(authenticated: User, accountForDeletionId: UserId): Txn[Boolean] =
    def isActiveAccount(uuid: UserId) = store.fetchAccountWith(uuid) { _.fold(false)(_.isActive) }
    (
      isActiveAccount(authenticated.uuid), // for when the user deactivates his own account
      accessControl.canAccess(authenticated.uuid, accountForDeletionId)
    ).mapN(_ && _)

  private def doMarkForDeletion(userId: UserId): Txn[ScheduledAccountDeletion] =
    store
      .markForDeletion(userId, delayUntilPermanentDeletion)
      .flatTap { _ => accessControl.revokeContextualAccess(userId, userId) }

  private def canAccountBeRestored(authenticated: User, accountToBeRestoredId: UserId): Txn[Boolean] =
    accessControl.canAccess(authenticated.uuid, accountToBeRestoredId)

private[http] object UsersRoutes:
  object UserIdVar:
    def unapply(uuid: String): Option[UserId] = uuid.parsed().toOption

  object Model:
    case class ScheduledForPermanentDeletionResponse(userId: UserId, permanentDeletionAt: Instant)

    extension (job: ScheduledAccountDeletion)
      def asResponsePayload: ScheduledForPermanentDeletionResponse =
        ScheduledForPermanentDeletionResponse(job.userId, job.permanentDeletionAt)

    object json:

      given Encoder[ScheduledForPermanentDeletionResponse] = new Encoder[ScheduledForPermanentDeletionResponse]:
        def apply(a: ScheduledForPermanentDeletionResponse): Json =
          Json.obj(
            "userId" -> a.userId.asJson,
            "permanentDeletionAt" -> a.permanentDeletionAt.asJson
          )
