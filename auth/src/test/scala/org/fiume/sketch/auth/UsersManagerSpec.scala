package org.fiume.sketch.auth

import cats.effect.IO
import cats.implicits.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import munit.Assertions.*
import org.fiume.sketch.shared.auth.domain.{Account, AccountState, ActivateAccountError, SoftDeleteAccountError, UserId}
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

  test("only the owner or an Admin can mark an account for deletion"):
    forAllF { (owner: UserCredentials, admin: UserCredentials, isAdminMarkingForDeletion: Boolean) =>
      val deletedAt = Instant.now()
      val permantDeletionDelay = 1.second
      for
        store <- makeEmptyUsersStore(makeFrozenClock(deletedAt), permantDeletionDelay)
        accessControl <- makeAccessControl()
        ownerId <- store.createAccount(owner).flatTap { id => accessControl.grantAccess(id, id, ContextualRole.Owner) }
        adminId <- store.createAccount(admin).flatTap { id => accessControl.grantGlobalAccess(id, GlobalRole.Admin) }
        authorisedId = if isAdminMarkingForDeletion then adminId else ownerId
        usersManager = UsersManager.make[IO, IO](store, accessControl, delayUntilPermanentDeletion)

        result <- usersManager.markAccountForDeletion(authorisedId, ownerId).map(_.rightOrFail)

        // The Api is idempotent in behaviour but not in response
        resp = usersManager.markAccountForDeletion(authorisedId, ownerId).map(_.leftOrFail)
        _ <-
          if isAdminMarkingForDeletion
          then assertIO(resp, SoftDeleteAccountError.AccountAlreadyPendingDeletion)
          else assertIO(resp, AccessDenied)

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

  test("users cannot mark an account for deletion without permission"):
    forAllF { (owner: UserCredentials, authed: UserCredentials, isSuperuser: Boolean) =>
      for
        store <- makeEmptyUsersStore()
        accessControl <- makeAccessControl()
        authedId <- store.createAccount(authed).flatTap { id => accessControl.grantAccess(id, id, ContextualRole.Owner) }
        _ <- accessControl.grantGlobalAccess(authedId, GlobalRole.Superuser).whenA(isSuperuser)
        ownerId <- store.createAccount(owner).flatTap { id => accessControl.grantAccess(id, id, ContextualRole.Owner) }

        usersManager = UsersManager.make[IO, IO](store, accessControl, delayUntilPermanentDeletion)
        result <- usersManager.markAccountForDeletion(authedId, ownerId).map(_.leftOrFail)
//
      yield assertEquals(result, AccessDenied)
    }

  test("users cannot mark an inexistent account for deletion"):
    forAllF { (ownerId: UserId, authed: UserCredentials, maybeGlobalRole: Option[GlobalRole]) =>
      for
        store <- makeEmptyUsersStore()
        accessControl <- makeAccessControl()
        authedId <- store.createAccount(authed).flatTap { id => accessControl.grantAccess(id, id, ContextualRole.Owner) }
        _ <- accessControl.grantGlobalAccess(authedId, maybeGlobalRole.someOrFail).whenA(maybeGlobalRole.isDefined)
        usersManager = UsersManager.make[IO, IO](store, accessControl, delayUntilPermanentDeletion)

        result <- usersManager.markAccountForDeletion(authedId, ownerId).map(_.leftOrFail)
//
      yield
        if maybeGlobalRole.exists(_ == GlobalRole.Admin)
        then assertEquals(result, SoftDeleteAccountError.AccountNotFound)
        else assertEquals(result, AccessDenied)
    }

  test("only Admin users can restore user accounts"):
    forAllF { (owner: UserCredentials, authed: UserCredentials) =>
      val accountReactivationDate = Instant.now
      val clock = makeFrozenClock(accountReactivationDate)
      for
        store <- makeEmptyUsersStore(clock)
        accessControl <- makeAccessControl()
        ownerId <- store.createAccount(owner).flatTap(store.markForDeletion(_, 0.seconds))
        adminId <- store.createAccount(authed).flatTap { accessControl.grantGlobalAccess(_, GlobalRole.Admin) }
        usersManager = UsersManager.make[IO, IO](store, accessControl, delayUntilPermanentDeletion)

        result <- usersManager.restoreAccount(adminId, ownerId).map(_.rightOrFail)

        _ <- IO {
          assertEquals(result.credentials, owner)
          assertEquals(result.state,
                       AccountState.Active(accountReactivationDate.truncatedTo(MILLIS))
          )
        }
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

  test("users cannot restore an account without permission"):
    forAllF { (owner: Account, authed: UserCredentials, isSuperuser: Boolean) =>
      for
        store <- makeUsersStoreForAccount(owner.copy(state = AccountState.SoftDeleted(Instant.now())))
        accessControl <- makeAccessControl()
        nonAdminUserId <- store.createAccount(authed).flatTap {
          accessControl.grantGlobalAccess(_, GlobalRole.Superuser).whenA(isSuperuser)
        }
        usersManager = UsersManager.make[IO, IO](store, accessControl, delayUntilPermanentDeletion)

        result <- usersManager.restoreAccount(nonAdminUserId, owner.uuid).map(_.leftOrFail)

        account <- store.fetchAccount(owner.uuid).map(_.someOrFail)
      yield
        assert(account.isMarkedForDeletion, clue = "account should remain marked for deletion")
        assertEquals(result, AccessDenied)
    }

  test("users cannot restore an inexistent account"):
    forAllF { (ownerId: UserId, authed: UserCredentials, maybeGlobalRole: Option[GlobalRole]) =>
      for
        store <- makeEmptyUsersStore()
        accessControl <- makeAccessControl()
        authedId <- store.createAccount(authed).flatTap {
          accessControl.grantGlobalAccess(_, maybeGlobalRole.someOrFail).whenA(maybeGlobalRole.isDefined)
        }
        usersManager = UsersManager.make[IO, IO](store, accessControl, delayUntilPermanentDeletion)

        result <- usersManager.restoreAccount(authedId, ownerId).map(_.leftOrFail)
//
      yield
        if maybeGlobalRole.exists(_ == GlobalRole.Admin)
        then assertEquals(result, ActivateAccountError.AccountNotFound)
        else assertEquals(result, AccessDenied)
    }

trait UsersManagerSpecContext:
  val delayUntilPermanentDeletion = 30.days
