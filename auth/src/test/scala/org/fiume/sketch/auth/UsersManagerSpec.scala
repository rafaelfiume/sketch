package org.fiume.sketch.auth

import cats.effect.IO
import cats.implicits.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import munit.Assertions.*
import org.fiume.sketch.shared.auth.domain.{Account, AccountState, ActivateAccountError, SoftDeleteAccountError}
import org.fiume.sketch.shared.auth.domain.Passwords.PlainPassword
import org.fiume.sketch.shared.auth.domain.User.{UserCredentials, Username}
import org.fiume.sketch.shared.auth.jobs.ScheduledAccountDeletion
import org.fiume.sketch.shared.auth.testkit.{UserGens, UsersStoreContext}
import org.fiume.sketch.shared.auth.testkit.AccountGens.given
import org.fiume.sketch.shared.auth.testkit.PasswordsGens.given
import org.fiume.sketch.shared.auth.testkit.UserGens.given
import org.fiume.sketch.shared.authorisation.{AccessDenied, ContextualRole, GlobalRole}
import org.fiume.sketch.shared.authorisation.testkit.AccessControlContext
import org.fiume.sketch.shared.authorisation.testkit.AccessControlGens.given
import org.fiume.sketch.shared.testkit.ClockContext
import org.fiume.sketch.shared.testkit.syntax.EitherSyntax.*
import org.fiume.sketch.shared.testkit.syntax.OptionSyntax.*
import org.scalacheck.ShrinkLowPriority
import org.scalacheck.effect.PropF.forAllF

import java.time.Instant
import java.time.temporal.ChronoUnit.MILLIS
import scala.concurrent.duration.*

class UsersManagerSpec
    extends CatsEffectSuite
    with ScalaCheckEffectSuite
    with UsersStoreContext
    with AccessControlContext
    with ClockContext
    with UsersManagerSpecContext
    with ShrinkLowPriority:

  override def scalaCheckTestParameters = super.scalaCheckTestParameters.withMinSuccessfulTests(1)

  test("user account creation succeeds with a unique username"):
    forAllF { (username: Username, password: PlainPassword, globalRole: Option[GlobalRole]) =>
      for
        usersStore <- makeEmptyUsersStore()
        accessControl <- makeAccessControl()
        usersManager = UsersManager.make[IO, IO](usersStore, accessControl, delayUntilPermanentDeletion)

        userId <- usersManager.createAccount(username, password, globalRole)

        account <- usersStore.fetchAccount(userId)
        userCanAccessHerOwnAccountDetails <- accessControl.canAccess(userId, userId)
        assignedGlobalRole <- accessControl.getGlobalRole(userId)
      yield
        assert(account.someOrFail.isActive)
        assert(userCanAccessHerOwnAccountDetails)
        if globalRole.isDefined then assertEquals(assignedGlobalRole.someOrFail, globalRole.someOrFail)
        else assert(assignedGlobalRole.isEmpty)
    }

  test("only the owner or an Admin can mark an account for deletion") {
    forAllF { (owner: UserCredentials, admin: UserCredentials, isAdminMarkingForDeletion: Boolean) =>
      val deletedAt = Instant.now()
      val permantDeletionDelay = 1.second
      for
        store <- makeEmptyUsersStore(makeFrozenClock(deletedAt), permantDeletionDelay)
        accessControl <- makeAccessControl()
        ownerId <- store.createAccount(owner).flatTap { id => accessControl.grantAccess(id, id, ContextualRole.Owner) }
        adminId <- store.createAccount(admin).flatTap { id => accessControl.grantGlobalAccess(id, GlobalRole.Admin) }
        authedId = if isAdminMarkingForDeletion then adminId else ownerId
        usersManager = UsersManager.make[IO, IO](store, accessControl, delayUntilPermanentDeletion)

        result <- usersManager.markAccountForDeletion(authedId, ownerId).map(_.rightOrFail)

        // The Api is idempotent in behaviour but not in response
        _ <- assertIO(
          usersManager.markAccountForDeletion(authedId, ownerId).map(_.leftOrFail),
          SoftDeleteAccountError.AccountAlreadyPendingDeletion
        )
        account <- store.fetchAccount(ownerId).map(_.someOrFail)
        grantRemoved <- accessControl.canAccess(ownerId, ownerId).map(!_)
        jobId <- store.getScheduledJob(ownerId).map(_.someOrFail.uuid)
      yield
        assert(account.isMarkedForDeletion)
        assert(grantRemoved)
        assertEquals(
          result,
          ScheduledAccountDeletion(
            jobId,
            ownerId,
            deletedAt.plusSeconds(permantDeletionDelay.toSeconds).truncatedTo(MILLIS)
          )
        )
    }
  }

  test("users are forbidden to attempt to mark an account for deletion with lack of permission") {
    forAllF { (owner: UserCredentials, ownerExists: Boolean, authed: UserCredentials, isSuperuser: Boolean) =>
      for
        store <- makeEmptyUsersStore()
        accessControl <- makeAccessControl()
        authedId <- store.createAccount(authed).flatTap { id => accessControl.grantAccess(id, id, ContextualRole.Owner) }
        _ <- accessControl.grantGlobalAccess(authedId, GlobalRole.Superuser).whenA(isSuperuser)
        ownerId <-
          if ownerExists then store.createAccount(owner).flatTap { id => accessControl.grantAccess(id, id, ContextualRole.Owner) }
          else UserGens.userIds.sample.someOrFail.pure[IO]

        usersManager = UsersManager.make[IO, IO](store, accessControl, delayUntilPermanentDeletion)
        result <- usersManager.markAccountForDeletion(authedId, ownerId).map(_.leftOrFail)
      yield assertEquals(result, AccessDenied)
    }
  }
  // TODO Check mark for deletion and restore account with AccountNotFound sad path

  test("only Admin users can restore user accounts"):
    forAllF { (owner: UserCredentials, authed: UserCredentials) =>
      for
        store <- makeEmptyUsersStore()
        accessControl <- makeAccessControl()
        ownerId <- store.createAccount(owner).flatTap(store.markForDeletion(_, 0.seconds))
        adminId <- store.createAccount(authed).flatTap { id => accessControl.grantGlobalAccess(id, GlobalRole.Admin) }
        usersManager = UsersManager.make[IO, IO](store, accessControl, delayUntilPermanentDeletion)

        result <- usersManager.restoreAccount(adminId, ownerId).map(_.rightOrFail)

        // The Api is idempotent in behaviour but not in response
        _ <- assertIO(
          usersManager.restoreAccount(adminId, ownerId).map(_.leftOrFail),
          ActivateAccountError.AccountAlreadyActive
        )
        account <- store.fetchAccount(ownerId).map(_.someOrFail)
        userCanAccessHerAccount <- accessControl.canAccess(ownerId, ownerId)
      yield
        assert(account.isActive, clue = "account should be active after restore")
        assert(userCanAccessHerAccount, clue = "user should be able to access her account after restore")
    }

  test("users are forbidden to attempt to restore an account without permission"):
    forAllF { (owner: Account, authed: UserCredentials, isSuperuser: Boolean) =>
      for
        store <- makeUsersStoreForAccount(owner.copy(state = AccountState.SoftDeleted(Instant.now())))
        accessControl <- makeAccessControl()
        nonAdminUserId <- store.createAccount(authed).flatTap { id =>
          accessControl.grantGlobalAccess(id, GlobalRole.Superuser).whenA(isSuperuser)
        }
        usersManager = UsersManager.make[IO, IO](store, accessControl, delayUntilPermanentDeletion)

        result <- usersManager.restoreAccount(nonAdminUserId, owner.uuid).map(_.leftOrFail)

        account <- store.fetchAccount(owner.uuid).map(_.someOrFail)
      yield
        assert(account.isMarkedForDeletion, clue = "account should remain marked for deletion")
        assertEquals(result, AccessDenied)
    }

trait UsersManagerSpecContext:
  val delayUntilPermanentDeletion = 30.days
