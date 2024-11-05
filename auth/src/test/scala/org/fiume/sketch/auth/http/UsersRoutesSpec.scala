package org.fiume.sketch.auth.http

import cats.effect.IO
import cats.implicits.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.fiume.sketch.auth.UsersManager
import org.fiume.sketch.shared.auth.domain.{Account, ActivateAccountError, SoftDeleteAccountError, User, UserId}
import org.fiume.sketch.shared.auth.domain.Passwords.PlainPassword
import org.fiume.sketch.shared.auth.domain.User.Username
import org.fiume.sketch.shared.auth.http.model.Users.ScheduledForPermanentDeletionResponse
import org.fiume.sketch.shared.auth.http.model.Users.json.given
import org.fiume.sketch.shared.auth.jobs.ScheduledAccountDeletion
import org.fiume.sketch.shared.auth.testkit.{AuthMiddlewareContext, UserGens}
import org.fiume.sketch.shared.auth.testkit.AccountGens.given
import org.fiume.sketch.shared.auth.testkit.UserGens.given
import org.fiume.sketch.shared.authorisation.{AccessDenied, GlobalRole}
import org.fiume.sketch.shared.common.jobs.JobId
import org.fiume.sketch.shared.common.testkit.JobGens.given
import org.fiume.sketch.shared.common.troubleshooting.ErrorInfo
import org.fiume.sketch.shared.common.troubleshooting.ErrorInfo.json.given
import org.fiume.sketch.shared.common.troubleshooting.syntax.ErrorInfoSyntax.*
import org.fiume.sketch.shared.testkit.{ContractContext, Http4sRoutesContext}
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
    with UsersManagerContext
    with ContractContext
    with ShrinkLowPriority:

  test("marks accounts for deletion"):
    forAllF { (authed: User, ownerId: UserId, jobId: JobId) =>
// given
      val deletedAt = Instant.now()
      val permantDeletionDelay = 1.second
      val scheduledJob = ScheduledAccountDeletion(
        jobId,
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
          jobId,
          ownerId,
          deletedAt.plusSeconds(permantDeletionDelay.toSeconds).truncatedTo(MILLIS)
        )
      )
    }

  // TODO Table test the other errors
  test("handles marking account for deletion errors") {
    forAllF { (ownerId: UserId, authed: User, isSuperuser: Boolean) =>
      val authMiddleware = makeAuthMiddleware(authenticated = authed)
      val usersManager = primeMarkAccountForDeletion(authed.uuid, ownerId, toReturn = AccessDenied.asLeft)
      val usersRoutes = new UsersRoutes[IO, IO](authMiddleware, usersManager)

      assertIO(
        send(DELETE(Uri.unsafeFromString(s"/users/${ownerId.value}")))
          .to(usersRoutes.router())
//
          .expectJsonResponseWith[ErrorInfo](Status.Forbidden),
        ErrorInfo.make("3000".code, "Unauthorised operation".message)
      )
    }
  }
  // TODO Check mark for deletion also with AccountAlreadyPendingDeletion and AccountNotFound sad path

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

  // TODO Table test the other errors
  test("handles restoring account errors"):
    forAllF { (ownerAccount: Account, authed: User) =>
      val authMiddleware = makeAuthMiddleware(authenticated = authed)
      val usersManager = primeRestoreAccount(authed.uuid, ownerAccount.uuid, toReturn = AccessDenied.asLeft)
      val usersRoutes = new UsersRoutes[IO, IO](authMiddleware, usersManager)

      assertIO(
        send(POST(Uri.unsafeFromString(s"/users/${ownerAccount.uuid.value}/restore")))
          .to(usersRoutes.router())
//
          .expectJsonResponseWith[ErrorInfo](Status.Forbidden),
        ErrorInfo.make("3000".code, "Unauthorised operation".message)
      )
    }

  test("ScheduledForPermanentDeletionResponse encode and decode form a bijective relationship"):
    assertBijectiveRelationshipBetweenEncoderAndDecoder[ScheduledForPermanentDeletionResponse](
      "auth/users/delete.response.json"
    )

trait UsersManagerContext:
  def primeMarkAccountForDeletion(
    expectedMarkingForDeletion: UserId,
    expectedToBeMarkedForDeletion: UserId,
    toReturn: Either[AccessDenied.type | SoftDeleteAccountError, ScheduledAccountDeletion]
  ): UsersManager[IO] = new UnimplementedUsersManager:
    override def markAccountForDeletion(
      markingForDeletion: UserId,
      toBeMarkedForDeletion: UserId
    ): IO[Either[AccessDenied.type | SoftDeleteAccountError, ScheduledAccountDeletion]] =
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
    override def restoreAccount(
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

    override def markAccountForDeletion(
      markingForDeletion: UserId,
      toBeMarkedForDeletion: UserId
    ): IO[Either[AccessDenied.type | SoftDeleteAccountError, ScheduledAccountDeletion]] = ???

    override def restoreAccount(
      restoringAccount: UserId,
      accountToBeRestored: UserId
    ): IO[Either[AccessDenied.type | ActivateAccountError, Account]] = ???
