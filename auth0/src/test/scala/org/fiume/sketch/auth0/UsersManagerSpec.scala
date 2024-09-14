package org.fiume.sketch.auth0

import cats.effect.IO
import cats.implicits.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import munit.Assertions.*
import org.fiume.sketch.authorisation.testkit.AccessControlContext
import org.fiume.sketch.shared.auth0.Passwords.PlainPassword
import org.fiume.sketch.shared.auth0.User.Username
import org.fiume.sketch.shared.auth0.testkit.PasswordsGens.given
import org.fiume.sketch.shared.auth0.testkit.UserGens.given
import org.fiume.sketch.shared.auth0.testkit.UsersStoreContext
import org.scalacheck.ShrinkLowPriority
import org.scalacheck.effect.PropF.forAllF

class UsersManagerSpec
    extends CatsEffectSuite
    with ScalaCheckEffectSuite
    with UsersStoreContext
    with AccessControlContext
    with ShrinkLowPriority:

  override def scalaCheckTestParameters = super.scalaCheckTestParameters.withMinSuccessfulTests(1)

  test("user account creation succeeds with a unique username"):
    forAllF { (username: Username, password: PlainPassword, isSuperuser: Boolean) =>
      for
        usersStore <- makeUsersStore()
        accessControl <- makeAccessControl()
        usersManager <- UsersManager.make[IO, IO](usersStore, accessControl)

        accountId <- usersManager.createAccount(username, password, isSuperuser)

        // TODO Extract Option syntax
        account <- usersStore.fetchAccount(username).map(_.get)
        canAccess <- accessControl.canAccess(accountId, accountId)
        canAccessGlobal <- accessControl.canAccessGlobal(accountId)
      yield
        assert(account.isActive)
        assert(canAccess)
        assertEquals(canAccessGlobal, isSuperuser)
    }
