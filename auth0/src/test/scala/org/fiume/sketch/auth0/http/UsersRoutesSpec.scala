package org.fiume.sketch.auth0.http

import cats.effect.IO
import cats.implicits.*
import io.circe.{Decoder, HCursor}
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.fiume.sketch.auth0.http.UsersRoutes.Model.ScheduledForPermanentDeletionResponse
import org.fiume.sketch.authorisation.{ContextualRole, GlobalRole}
import org.fiume.sketch.authorisation.ContextualRole.Owner
import org.fiume.sketch.authorisation.GlobalRole.Admin
import org.fiume.sketch.authorisation.testkit.AccessControlContext
import org.fiume.sketch.shared.auth0.domain.{Account, AccountState, User, UserId}
import org.fiume.sketch.shared.auth0.domain.AccountState.*
import org.fiume.sketch.shared.auth0.domain.User.UserCredentials
import org.fiume.sketch.shared.auth0.testkit.{AuthMiddlewareContext, UserGens, UsersStoreContext}
import org.fiume.sketch.shared.auth0.testkit.AccountGens.given
import org.fiume.sketch.shared.auth0.testkit.UserGens.given
import org.fiume.sketch.shared.testkit.{ClockContext, Http4sRoutesContext}
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
    with UsersRoutesSpecContext
    with ShrinkLowPriority:

  test("marks user for deletion") {
    forAllF { (user: UserCredentials) =>
      val deletedAt = Instant.now()
      val permantDeletionDelay = 1.second
      for
        store <- makeEmptyUsersStore(makeFrozenClock(deletedAt), permantDeletionDelay)
        accessControl <- makeAccessControl()
        userId <- store.createAccount(user).flatTap { id => accessControl.grantAccess(id, id, Owner) }
        request = DELETE(Uri.unsafeFromString(s"/users/${userId.value}"))
        authMiddleware = makeAuthMiddleware(authenticated = User(userId, user.username))
        usersRoutes = new UsersRoutes[IO, IO](authMiddleware, accessControl, store, delayUntilPermanentDeletion)

        result <- send(request)
          .to(usersRoutes.router())
//
          .expectJsonResponseWith[ScheduledForPermanentDeletionResponse](Status.Ok)
        account <- store.fetchAccount(userId).map(_.someOrFail)
        grantRemoved <- accessControl.canAccess(userId, userId).map(!_)
      yield
        assert(!account.isActive)
        assert(grantRemoved)
        assertEquals(
          result,
          ScheduledForPermanentDeletionResponse(userId, deletedAt.plusSeconds(permantDeletionDelay.toSeconds).truncatedTo(MILLIS))
        )
    }
  }

  test("attempt to delete a non-existent account or with lack of permission results in 403") {
    forAllF { (authedUser: UserCredentials, maybeAnotherUser: UserCredentials, anotherUserExists: Boolean) =>
      for
        store <- makeEmptyUsersStore()
        accessControl <- makeAccessControl()
        authedUserId <- store.createAccount(authedUser).flatTap { id => accessControl.grantAccess(id, id, Owner) }
        anotherUserId <-
          if anotherUserExists then
            store.createAccount(maybeAnotherUser).flatTap { id => accessControl.grantAccess(id, id, Owner) }
          else UserGens.userIds.sample.someOrFail.pure[IO]
        authMiddleware = makeAuthMiddleware(authenticated = User(authedUserId, authedUser.username))
        usersRoutes = new UsersRoutes[IO, IO](authMiddleware, accessControl, store, delayUntilPermanentDeletion)
        request = DELETE(Uri.unsafeFromString(s"/users/${anotherUserId.value}"))

        _ <- send(request)
          .to(usersRoutes.router())
//
          .expectEmptyResponseWith(Status.Forbidden)
      yield ()
    }
  }

  test("only users with Admin role can restore user accounts"):
    forAllF { (authed: UserCredentials, userToBeRestored: Account, isActive: Boolean) =>
      for
        store <- makeUsersStoreForAccount(userToBeRestored.copy(state = Active(Instant.now())))
        _ <- store.markForDeletion(userToBeRestored.uuid, 1.day).whenA(!isActive)
        accessControl <- makeAccessControl()
        authedId <- store.createAccount(authed).flatTap { id => accessControl.grantGlobalAccess(id, Admin) }
        authMiddleware = makeAuthMiddleware(authenticated = User(authedId, authed.username))
        request = PUT(Uri.unsafeFromString(s"/users/${userToBeRestored.uuid.value}/restore"))
        usersRoutes = new UsersRoutes[IO, IO](authMiddleware, accessControl, store, delayUntilPermanentDeletion)

        _ <- send(request).to(usersRoutes.router()).expectEmptyResponseWith(Status.NoContent)

        // idempotence
        _ <- send(request).to(usersRoutes.router()).expectEmptyResponseWith(Status.NoContent)
        account <- store.fetchAccount(userToBeRestored.uuid).map(_.someOrFail)
      yield assert(account.isActive)
    }

  test("attempt to restore an account without permission results in 403"):
    forAllF { (authed: UserCredentials, userToBeRestored: Account, isSuperuser: Boolean) =>
      for
        store <- makeUsersStoreForAccount(userToBeRestored.copy(state = SoftDeleted(Instant.now())))
        accessControl <- makeAccessControl()
        authedId <- store.createAccount(authed).flatTap { id =>
          accessControl.grantGlobalAccess(id, GlobalRole.Superuser).whenA(isSuperuser)
        }
        authMiddleware = makeAuthMiddleware(authenticated = User(authedId, authed.username))
        request = PUT(Uri.unsafeFromString(s"/users/${userToBeRestored.uuid.value}/restore"))
        usersRoutes = new UsersRoutes[IO, IO](authMiddleware, accessControl, store, delayUntilPermanentDeletion)

        _ <- send(request)
          .to(usersRoutes.router())
//
          .expectEmptyResponseWith(Status.Forbidden)
        account <- store.fetchAccount(userToBeRestored.uuid).map(_.someOrFail)
      yield assert(account.isMarkedForDeletion, clue = "account should remain marked for deletion")
    }

  // Note: Skipping contract tests to speed up development

trait UsersRoutesSpecContext:
  val delayUntilPermanentDeletion = 30.days

  // TODO Define Encoders and Decoders in the same place?
  given Decoder[UserId] = Decoder.decodeUUID.map(UserId(_))
  given Decoder[ScheduledForPermanentDeletionResponse] = new Decoder[ScheduledForPermanentDeletionResponse]:
    override def apply(c: HCursor): Decoder.Result[ScheduledForPermanentDeletionResponse] =
      for
        userId <- c.downField("userId").as[UserId]
        permanentDeletionAt <- c.downField("permanentDeletionAt").as[Instant]
      yield ScheduledForPermanentDeletionResponse(userId, permanentDeletionAt)
