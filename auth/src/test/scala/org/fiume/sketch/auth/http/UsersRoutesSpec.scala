package org.fiume.sketch.auth.http

import cats.effect.IO
import cats.implicits.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.fiume.sketch.auth.accounts.UsersManager
import org.fiume.sketch.shared.auth.{User, UserId}
import org.fiume.sketch.shared.auth.Passwords.PlainPassword
import org.fiume.sketch.shared.auth.User.Username
import org.fiume.sketch.shared.auth.accounts.{Account, AccountDeletionEvent, ActivateAccountError, SoftDeleteAccountError}
import org.fiume.sketch.shared.auth.accounts.ActivateAccountError.AccountAlreadyActive
import org.fiume.sketch.shared.auth.accounts.SoftDeleteAccountError.{AccountAlreadyPendingDeletion, AccountNotFound}
import org.fiume.sketch.shared.auth.http.model.Users.ScheduledForPermanentDeletionResponse
import org.fiume.sketch.shared.auth.http.model.Users.json.given
import org.fiume.sketch.shared.auth.testkit.{AuthMiddlewareContext, UserGens}
import org.fiume.sketch.shared.auth.testkit.AccountGens.given
import org.fiume.sketch.shared.auth.testkit.UserGens.given
import org.fiume.sketch.shared.authorisation.{AccessDenied, GlobalRole}
import org.fiume.sketch.shared.common.events.EventId
import org.fiume.sketch.shared.common.testkit.EventGens.given
import org.fiume.sketch.shared.common.troubleshooting.ErrorInfo
import org.fiume.sketch.shared.common.troubleshooting.ErrorInfo.json.given
import org.fiume.sketch.shared.common.troubleshooting.syntax.ErrorInfoSyntax.*
import org.fiume.sketch.shared.testkit.{ContractContext, Http4sRoutesContext}
import org.http4s.*
import org.http4s.client.dsl.io.*
import org.http4s.dsl.io.*
import org.scalacheck.ShrinkLowPriority
import org.scalacheck.effect.PropF.forAllF

import java.time.Instant
import java.time.temporal.ChronoUnit.MILLIS
import scala.concurrent.duration.*

class UsersRoutesSpec
    extends CatsEffectSuite
    with ScalaCheckEffectSuite
    with Http4sRoutesContext
    with AuthMiddlewareContext
    with UsersManagerContext
    with ContractContext
    with ShrinkLowPriority:

  test("marks accounts for deletion"):
    forAllF { (authed: User, ownerId: UserId, eventId: EventId) =>
// given
      val deletedAt = Instant.now()
      val permantDeletionDelay = 1.second
      val scheduledJob = AccountDeletionEvent.scheduled(
        eventId,
        ownerId,
        deletedAt.plusSeconds(permantDeletionDelay.toSeconds).truncatedTo(MILLIS)
      )
      val authMiddleware = makeAuthMiddleware(authenticated = authed)
      val usersManager = primeMarkAccountForDeletion(authed.uuid, ownerId, toReturn = scheduledJob.asRight)
      val usersRoutes = new UsersRoutes[IO, IO](authMiddleware, usersManager)
// when
      assertIO(
        send(DELETE(Uri.unsafeFromString(s"/users/${ownerId.value}")))
          .to(usersRoutes.router())
// then
          .expectJsonResponseWith[ScheduledForPermanentDeletionResponse](Status.Ok),
        ScheduledForPermanentDeletionResponse(
          eventId,
          ownerId,
          deletedAt.plusSeconds(permantDeletionDelay.toSeconds).truncatedTo(MILLIS)
        )
      )
    }

  List(
    // format: off
    ("access is denied"       , AccessDenied                 , Status.Forbidden, ErrorInfo.make("3000".code, "Unauthorised operation".message)),
    ("invalid state transtion", AccountAlreadyPendingDeletion, Status.Conflict , ErrorInfo.make("1200".code, "Account already marked for deletion".message)),
    ("account is not found"   , AccountNotFound              , Status.NotFound , ErrorInfo.make("1201".code, "Account not found".message))
    // format: on
  ).foreach { (description, error, expectedStatusCode, expectedErrorInfo) =>
    test(s"handles marking account for deletion when $description") {
      forAllF { (ownerId: UserId, authed: User) =>
        val authMiddleware = makeAuthMiddleware(authenticated = authed)
        val usersManager = primeMarkAccountForDeletion(authed.uuid, ownerId, toReturn = error.asLeft)
        val usersRoutes = new UsersRoutes[IO, IO](authMiddleware, usersManager)

        assertIO(
          send(DELETE(Uri.unsafeFromString(s"/users/${ownerId.value}")))
            .to(usersRoutes.router())
//
            .expectJsonResponseWith[ErrorInfo](expectedStatusCode),
          expectedErrorInfo
        )
      }
    }
  }

  test("restores user accounts"):
    forAllF { (ownerAccount: Account, authed: User) =>
      val authMiddleware = makeAuthMiddleware(authenticated = authed)
      val usersManager = primeRestoreAccount(authed.uuid, ownerAccount.uuid, toReturn = ownerAccount.asRight)
      val usersRoutes = new UsersRoutes[IO, IO](authMiddleware, usersManager)

      send(POST(Uri.unsafeFromString(s"/users/${ownerAccount.uuid.value}/restore")))
        .to(usersRoutes.router())
//
        .expectEmptyResponseWith(Status.NoContent)
    }

  List(
    // format: off
    ("access is denied"       , AccessDenied                        , Status.Forbidden, ErrorInfo.make("3000".code, "Unauthorised operation".message)),
    ("invalid state transtion", AccountAlreadyActive                , Status.Conflict , ErrorInfo.make("1210".code, "Account is already active".message)),
    ("account is not found"   , ActivateAccountError.AccountNotFound, Status.NotFound , ErrorInfo.make("1211".code, "Account not found".message))
    // format: on
  ).foreach { (description, error, expectedStatusCode, expectedErrorInfo) =>
    test(s"handles restoring account when $description"):
      forAllF { (ownerAccount: Account, authed: User) =>
        val authMiddleware = makeAuthMiddleware(authenticated = authed)
        val usersManager = primeRestoreAccount(authed.uuid, ownerAccount.uuid, toReturn = error.asLeft)
        val usersRoutes = new UsersRoutes[IO, IO](authMiddleware, usersManager)

        assertIO(
          send(POST(Uri.unsafeFromString(s"/users/${ownerAccount.uuid.value}/restore")))
            .to(usersRoutes.router())
//
            .expectJsonResponseWith[ErrorInfo](expectedStatusCode),
          expectedErrorInfo
        )
      }
  }

  test("ScheduledForPermanentDeletionResponse encode and decode form a bijective relationship"):
    assertBijectiveRelationshipBetweenEncoderAndDecoder[ScheduledForPermanentDeletionResponse](
      "auth/users/delete.response.json"
    )

trait UsersManagerContext:
  def primeMarkAccountForDeletion(
    expectedMarkingForDeletion: UserId,
    expectedToBeMarkedForDeletion: UserId,
    toReturn: Either[AccessDenied.type | SoftDeleteAccountError, AccountDeletionEvent.Scheduled]
  ): UsersManager[IO] = new UnimplementedUsersManager:
    override def attemptToMarkAccountForDeletion(
      markingForDeletion: UserId,
      toBeMarkedForDeletion: UserId
    ): IO[Either[AccessDenied.type | SoftDeleteAccountError, AccountDeletionEvent.Scheduled]] =
      if markingForDeletion === expectedMarkingForDeletion && toBeMarkedForDeletion === expectedToBeMarkedForDeletion then
        toReturn.pure[IO]
      else
        munit.Assertions.fail(
          s"received unexpected arguments markingForDeletion=$markingForDeletion, toBeMarkedForDeletion=$toBeMarkedForDeletion"
        )

  def primeRestoreAccount(
    expectedRestoringAccount: UserId,
    expectedAccountToBeRestoredId: UserId,
    toReturn: Either[AccessDenied.type | ActivateAccountError, Account]
  ): UsersManager[IO] = new UnimplementedUsersManager:
    override def attemptToRestoreAccount(
      restoringAccount: UserId,
      accountToBeRestored: UserId
    ): IO[Either[AccessDenied.type | ActivateAccountError, Account]] =
      if restoringAccount === expectedRestoringAccount && accountToBeRestored === expectedAccountToBeRestoredId then
        toReturn.pure[IO]
      else
        munit.Assertions.fail(
          s"received unexpected arguments restoringAccount=$restoringAccount, accountToBeRestored=$accountToBeRestored"
        )

  private class UnimplementedUsersManager() extends UsersManager[IO]:
    override def createAccount(username: Username, password: PlainPassword, globalRole: Option[GlobalRole]): IO[UserId] = ???

    override def attemptToMarkAccountForDeletion(
      markingForDeletion: UserId,
      toBeMarkedForDeletion: UserId
    ): IO[Either[AccessDenied.type | SoftDeleteAccountError, AccountDeletionEvent.Scheduled]] = ???

    override def attemptToRestoreAccount(
      restoringAccount: UserId,
      accountToBeRestored: UserId
    ): IO[Either[AccessDenied.type | ActivateAccountError, Account]] = ???

    override def restoreAccount(userId: UserId): IO[Either[ActivateAccountError, Account]] = ???
