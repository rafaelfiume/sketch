package org.fiume.sketch.auth.http

import cats.effect.IO
import cats.implicits.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.fiume.sketch.auth.http.UsersRoutes.Model.ScheduledForPermanentDeletionResponse
import org.fiume.sketch.auth.http.UsersRoutes.Model.json.given
import org.fiume.sketch.shared.auth.domain.{Account, AccountState, User, UserId}
import org.fiume.sketch.shared.auth.domain.AccountState.*
import org.fiume.sketch.shared.auth.domain.User.UserCredentials
import org.fiume.sketch.shared.auth.testkit.{AuthMiddlewareContext, UserGens, UsersStoreContext}
import org.fiume.sketch.shared.auth.testkit.AccountGens.given
import org.fiume.sketch.shared.auth.testkit.UserGens.given
import org.fiume.sketch.shared.authorisation.{ContextualRole, GlobalRole}
import org.fiume.sketch.shared.authorisation.ContextualRole.Owner
import org.fiume.sketch.shared.authorisation.GlobalRole.Admin
import org.fiume.sketch.shared.authorisation.testkit.AccessControlContext
import org.fiume.sketch.shared.testkit.{ClockContext, ContractContext, Http4sRoutesContext}
import org.fiume.sketch.shared.testkit.syntax.OptionSyntax.*
import org.http4s.*
import org.http4s.client.dsl.io.*
import org.http4s.dsl.io.*
import org.scalacheck.{Arbitrary, ShrinkLowPriority}
import org.scalacheck.effect.PropF.forAllF

import java.time.Instant
import java.time.temporal.ChronoUnit.MILLIS
import scala.concurrent.duration.*

class UsersRoutesSpec
    extends CatsEffectSuite
    with ScalaCheckEffectSuite
    with Http4sRoutesContext
    with AuthMiddlewareContext
    with AccessControlContext
    with UsersStoreContext
    with ClockContext
    with ContractContext
    with UsersRoutesSpecContext
    with ShrinkLowPriority:

  test("only the owner or an Admin can mark an account for deletion") {
    forAllF { (owner: UserCredentials, admin: UserCredentials, isAdminMarkingForDeletion: Boolean) =>
      val deletedAt = Instant.now()
      val permantDeletionDelay = 1.second
      for
        store <- makeEmptyUsersStore(makeFrozenClock(deletedAt), permantDeletionDelay)
        accessControl <- makeAccessControl()
        ownerId <- store.createAccount(owner).flatTap { id => accessControl.grantAccess(id, id, Owner) }
        adminId <- store.createAccount(admin).flatTap { id => accessControl.grantGlobalAccess(id, GlobalRole.Admin) }
        request = DELETE(Uri.unsafeFromString(s"/users/${ownerId.value}"))
        authMiddleware = makeAuthMiddleware(authenticated =
          if isAdminMarkingForDeletion then User(adminId, admin.username) else User(ownerId, owner.username)
        )
        usersRoutes = new UsersRoutes[IO, IO](authMiddleware, accessControl, store, delayUntilPermanentDeletion)

        result <- send(request)
          .to(usersRoutes.router())
//
          .expectJsonResponseWith[ScheduledForPermanentDeletionResponse](Status.Ok)
        account <- store.fetchAccount(ownerId).map(_.someOrFail)
        grantRemoved <- accessControl.canAccess(ownerId, ownerId).map(!_)
      yield
        assert(account.isMarkedForDeletion)
        assert(grantRemoved)
        assertEquals(
          result,
          ScheduledForPermanentDeletionResponse(
            ownerId,
            deletedAt.plusSeconds(permantDeletionDelay.toSeconds).truncatedTo(MILLIS)
          )
        )
    }
  }

  test("attempt to mark for deletion a non-existent account or with lack of permission results in 403") {
    forAllF { (owner: UserCredentials, ownerExists: Boolean, authed: UserCredentials, isSuperuser: Boolean) =>
      for
        store <- makeEmptyUsersStore()
        accessControl <- makeAccessControl()
        authedId <- store.createAccount(authed).flatTap { id => accessControl.grantAccess(id, id, Owner) }
        _ <- accessControl.grantGlobalAccess(authedId, GlobalRole.Superuser).whenA(isSuperuser)
        ownerId <-
          if ownerExists then store.createAccount(owner).flatTap { id => accessControl.grantAccess(id, id, Owner) }
          else UserGens.userIds.sample.someOrFail.pure[IO]
        authMiddleware = makeAuthMiddleware(authenticated = User(authedId, authed.username))
        usersRoutes = new UsersRoutes[IO, IO](authMiddleware, accessControl, store, delayUntilPermanentDeletion)
        request = DELETE(Uri.unsafeFromString(s"/users/${ownerId.value}"))

        _ <- send(request)
          .to(usersRoutes.router())
//
          .expectEmptyResponseWith(Status.Forbidden)
      yield ()
    }
  }

  test("only users with Admin role can restore user accounts"):
    forAllF { (owner: UserCredentials, authed: UserCredentials) =>
      for
        store <- makeEmptyUsersStore()
        accessControl <- makeAccessControl()
        ownerId <- store.createAccount(owner).flatTap { id => accessControl.grantAccess(id, id, Owner) }
        authedId <- store.createAccount(authed).flatTap { id => accessControl.grantGlobalAccess(id, Admin) }
        authMiddleware = makeAuthMiddleware(authenticated = User(authedId, authed.username))
        deleteRequest = DELETE(Uri.unsafeFromString(s"/users/${ownerId.value}"))
        restoreRequest = PUT(Uri.unsafeFromString(s"/users/${ownerId.value}/restore"))
        usersRoutes = new UsersRoutes[IO, IO](authMiddleware, accessControl, store, delayUntilPermanentDeletion)
        _ <- send(deleteRequest).to(usersRoutes.router()).expectJsonResponseWith[ScheduledForPermanentDeletionResponse](Status.Ok)

        _ <- send(restoreRequest).to(usersRoutes.router()).expectEmptyResponseWith(Status.NoContent)
        // idempotence
        _ <- send(restoreRequest)
          .to(usersRoutes.router())
//
          .expectEmptyResponseWith(Status.NoContent)
        account <- store.fetchAccount(ownerId).map(_.someOrFail)
        userCanAccessHerAccount <- accessControl.canAccess(ownerId, ownerId)
      yield
        assert(account.isActive, clue = "account should be active after restore")
        assert(userCanAccessHerAccount, clue = "user should be able to access her account after restore")
    }

  test("attempt to restore an account without permission results in 403"):
    forAllF { (owner: Account, authed: UserCredentials, isSuperuser: Boolean) =>
      for
        store <- makeUsersStoreForAccount(owner.copy(state = SoftDeleted(Instant.now())))
        accessControl <- makeAccessControl()
        authedId <- store.createAccount(authed).flatTap { id =>
          accessControl.grantGlobalAccess(id, GlobalRole.Superuser).whenA(isSuperuser)
        }
        authMiddleware = makeAuthMiddleware(authenticated = User(authedId, authed.username))
        request = PUT(Uri.unsafeFromString(s"/users/${owner.uuid.value}/restore"))
        usersRoutes = new UsersRoutes[IO, IO](authMiddleware, accessControl, store, delayUntilPermanentDeletion)

        _ <- send(request)
          .to(usersRoutes.router())
//
          .expectEmptyResponseWith(Status.Forbidden)
        account <- store.fetchAccount(owner.uuid).map(_.someOrFail)
      yield assert(account.isMarkedForDeletion, clue = "account should remain marked for deletion")
    }

  test("ScheduledForPermanentDeletionResponse encode and decode form a bijective relationship"):
    assertBijectiveRelationshipBetweenEncoderAndDecoder[ScheduledForPermanentDeletionResponse](
      "auth/users/delete.response.json"
    )

trait UsersRoutesSpecContext:
  val delayUntilPermanentDeletion = 30.days