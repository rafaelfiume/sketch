package org.fiume.sketch.auth0

import cats.effect.IO
import cats.implicits.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import munit.Assertions.*
import org.fiume.sketch.shared.auth0.Passwords.PlainPassword
import org.fiume.sketch.shared.auth0.User.Username
import org.fiume.sketch.shared.auth0.testkit.PasswordsGens.given
import org.fiume.sketch.shared.auth0.testkit.UserGens.given
import org.fiume.sketch.shared.auth0.testkit.UsersStoreContext
import org.scalacheck.ShrinkLowPriority
import org.scalacheck.effect.PropF.forAllF

class UsersManagerSpec extends CatsEffectSuite with ScalaCheckEffectSuite with UsersStoreContext with ShrinkLowPriority:
  test("registre a user with a unique username"):
    forAllF { (username: Username, password: PlainPassword) =>
      for
        usersStore <- makeUsersStore()

        usersManager <- UsersManager.make[IO, IO](usersStore)
        result <- usersManager.registreUser(username, password)

        registred <- usersStore.fetchUser(result.uuid)
        _ <- IO {
          assertEquals(result.some, registred)
          assertEquals(result.username, username)
        }
      yield ()
    }

  // TODO
  // test("registre a user with a duplicate username fails"):
