package org.fiume.sketch.auth0.http

import cats.effect.IO
import cats.implicits.*
import munit.{CatsEffectSuite, ScalaCheckSuite}
import org.fiume.sketch.authorisation.ContextualRole
import org.fiume.sketch.authorisation.ContextualRole.Owner
import org.fiume.sketch.authorisation.testkit.AccessControlContext
import org.fiume.sketch.shared.auth0.User
import org.fiume.sketch.shared.auth0.User.UserCredentials
import org.fiume.sketch.shared.auth0.testkit.{AuthMiddlewareContext, UserGens, UsersStoreContext}
import org.fiume.sketch.shared.auth0.testkit.UserGens.given
import org.fiume.sketch.shared.testkit.Http4sTestingRoutesDsl
import org.fiume.sketch.shared.testkit.syntax.OptionSyntax.*
import org.http4s.*
import org.http4s.client.dsl.io.*
import org.http4s.dsl.io.*
import org.scalacheck.ShrinkLowPriority
import org.scalacheck.effect.PropF.forAllF

class UsersRoutesSpec
    extends CatsEffectSuite
    with ScalaCheckSuite
    with Http4sTestingRoutesDsl
    with AuthMiddlewareContext
    with AccessControlContext
    with UsersStoreContext
    with ShrinkLowPriority:

  // override def scalaCheckTestParameters = super.scalaCheckTestParameters.withMinSuccessfulTests(10)

  test("marks user for deletion") {
    forAllF { (user: UserCredentials) =>
      for
        store <- makeUsersStore()
        accessControl <- makeAccessControl()
        userId <- store.store(user).flatTap { id => accessControl.grantAccess(id, id, Owner) }
        request = DELETE(Uri.unsafeFromString(s"/users/${userId.value}"))
        authMiddleware = makeAuthMiddleware(authenticated = User(userId, user.username))
        usersRoutes = new UsersRoutes[IO, IO](authMiddleware, accessControl, store)

        _ <- send(request)
          .to(usersRoutes.router())
//
          .expectEmptyResponseWith(Status.Ok) // TODO Test response payload contract
        account <- store.fetchAccount(user.username).map(_.someOrFail)
        grantRemoved <- accessControl.canAccess(userId, userId).map(!_)
      yield
        assert(!account.isActive) // TODO Check SoftDeleted state
        assert(grantRemoved)
    }
  }

  test("returns 403 when user is not allowed to delete or does not exist") {
    forAllF { (authedUser: UserCredentials, maybeAnotherUser: UserCredentials, anotherUserExists: Boolean) =>
      for
        store <- makeUsersStore()
        accessControl <- makeAccessControl()
        authedUserId <- store.store(authedUser).flatTap { id => accessControl.grantAccess(id, id, Owner) }
        anotherUserId <-
          if anotherUserExists then store.store(maybeAnotherUser).flatTap { id => accessControl.grantAccess(id, id, Owner) }
          else UserGens.userIds.sample.someOrFail.pure[IO]
        authMiddleware = makeAuthMiddleware(authenticated = User(authedUserId, authedUser.username))
        usersRoutes = new UsersRoutes[IO, IO](authMiddleware, accessControl, store)
        request = DELETE(Uri.unsafeFromString(s"/users/${anotherUserId.value}"))

        _ <- send(request)
          .to(usersRoutes.router())
//
          .expectEmptyResponseWith(Status.Forbidden)
      yield ()
    }
  }

  // TODO Test response payload contract
