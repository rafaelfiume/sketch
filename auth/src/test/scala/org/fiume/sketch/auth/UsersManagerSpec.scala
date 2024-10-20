package org.fiume.sketch.auth

import cats.effect.IO
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import munit.Assertions.*
import org.fiume.sketch.shared.auth.domain.Passwords.PlainPassword
import org.fiume.sketch.shared.auth.domain.User.Username
import org.fiume.sketch.shared.auth.testkit.PasswordsGens.given
import org.fiume.sketch.shared.auth.testkit.UserGens.given
import org.fiume.sketch.shared.auth.testkit.UsersStoreContext
import org.fiume.sketch.shared.authorisation.GlobalRole
import org.fiume.sketch.shared.authorisation.testkit.AccessControlContext
import org.fiume.sketch.shared.testkit.syntax.OptionSyntax.*
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
        usersStore <- makeEmptyUsersStore()
        accessControl <- makeAccessControl()
        usersManager <- UsersManager.make[IO, IO](usersStore, accessControl)

        userId <- usersManager.createAccount(username, password, isSuperuser)

        account <- usersStore.fetchAccount(userId)
        userCanAccessHerOwnAccountDetails <- accessControl.canAccess(userId, userId)
        userGlobalRole <- accessControl.getGlobalRole(userId)
      yield
        assert(account.someOrFail.isActive)
        assert(userCanAccessHerOwnAccountDetails)
        if isSuperuser then assertEquals(userGlobalRole.someOrFail, GlobalRole.Superuser)
        else assert(userGlobalRole.isEmpty)
    }
