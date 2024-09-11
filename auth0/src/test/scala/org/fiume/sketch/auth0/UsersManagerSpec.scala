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

  test("user registration succeeds with a unique username"):
    forAllF { (username: Username, password: PlainPassword, isSuperuser: Boolean) =>
      for
        usersStore <- makeUsersStore()
        accessControl <- makeAccessControl()
        usersManager <- UsersManager.make[IO, IO](usersStore, accessControl)

        result <- usersManager.registreUser(username, password, isSuperuser)

        registred <- usersStore.fetchUser(result.uuid)
        canAccessGlobal <- accessControl.canAccessGlobal(result.uuid)
        _ <- IO {
          assertEquals(result.some, registred)
          assertEquals(result.username, username)
          assertEquals(canAccessGlobal, isSuperuser)
        }
      yield ()
    }
