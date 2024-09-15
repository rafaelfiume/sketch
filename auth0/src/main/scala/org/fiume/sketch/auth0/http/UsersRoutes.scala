package org.fiume.sketch.auth0.http

import cats.FlatMap
import cats.effect.Concurrent
import cats.implicits.*
import org.fiume.sketch.auth0.http.UsersRoutes.UserIdVar
import org.fiume.sketch.authorisation.AccessControl
import org.fiume.sketch.shared.app.EntityId.given
import org.fiume.sketch.shared.app.syntax.StoreSyntax.*
import org.fiume.sketch.shared.auth0.{User, UserId}
import org.fiume.sketch.shared.auth0.algebras.UsersStore
import org.http4s.{HttpRoutes, *}
import org.http4s.dsl.Http4sDsl
import org.http4s.server.{AuthMiddleware, Router}

class UsersRoutes[F[_]: Concurrent, Txn[_]: FlatMap](
  authMiddleware: AuthMiddleware[F, User],
  accessControl: AccessControl[F, Txn],
  store: UsersStore[F, Txn]
) extends Http4sDsl[F]:

  private val prefix = "/"

  // enable Store's syntax
  given UsersStore[F, Txn] = store

  def router(): HttpRoutes[F] = Router(prefix -> authMiddleware(authedRoutes))

  // TODO Add to acceptance or load test
  private val authedRoutes: AuthedRoutes[User, F] =
    AuthedRoutes.of { case DELETE -> Root / "users" / UserIdVar(uuid) as user =>
      for
        // TODO replace: `canAccess` by `canDelete`. Or make `canAccess` accept a `privilege` as param?
        // TODO make `Admin` able to delete any user
        // TODO Implement a version of canAccess that performs the check and performs the action?
        res <- accessControl
          .canAccess(user.uuid, user.uuid)
          .commit()
          .ifM(
            ifTrue = store.markForDeletion(user.uuid).commit() *> Ok(), // TODO Return response payload
            ifFalse = Forbidden("You are not allowed to delete this user")
          )
      yield res
    }

private[http] object UsersRoutes:
  object UserIdVar:
    def unapply(uuid: String): Option[UserId] = uuid.parsed().toOption
