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
import org.fiume.sketch.shared.app.http4s.JsonCodecs.given
import org.fiume.sketch.shared.app.syntax.StoreSyntax.*
import org.fiume.sketch.shared.auth0.AccountConfig
import org.fiume.sketch.shared.auth0.algebras.UsersStore
import org.fiume.sketch.shared.auth0.domain.{User, UserId}
import org.fiume.sketch.shared.auth0.jobs.ScheduledAccountDeletion
import org.http4s.{HttpRoutes, *}
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.Http4sDsl
import org.http4s.server.{AuthMiddleware, Router}

import java.time.Instant

class UsersRoutes[F[_]: Concurrent, Txn[_]: Sync](
  config: AccountConfig,
  authMiddleware: AuthMiddleware[F, User],
  accessControl: AccessControl[F, Txn],
  store: UsersStore[F, Txn]
) extends Http4sDsl[F]:

  private val prefix = "/"

  // enable Store's syntax
  given UsersStore[F, Txn] = store

  def router(): HttpRoutes[F] = Router(prefix -> authMiddleware(authedRoutes))

  private val authedRoutes: AuthedRoutes[User, F] =
    AuthedRoutes.of { case DELETE -> Root / "users" / UserIdVar(uuid) as user =>
      for
        canDelete <- isAuthorised(user, uuid)
        res <-
          if canDelete then Ok(doMarkForDeletion(uuid).map(_.asResponsePayload))
          else Forbidden()
      yield res
    }

  private def isAuthorised(authenticated: User, accountForDeletionId: UserId): F[Boolean] = (
    store.fetchAccount(authenticated.username).map { _.map(_.isActive).getOrElse(false) },
    accessControl.canAccess(authenticated.uuid, accountForDeletionId)
  ).mapN(_ && _).commit()

  private def doMarkForDeletion(userId: UserId): F[ScheduledAccountDeletion] =
    store
      .markForDeletion(userId, config.delayUntilPermanentDeletion)
      .flatTap { _ => accessControl.revokeContextualAccess(userId, userId) }
      .commit()

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
