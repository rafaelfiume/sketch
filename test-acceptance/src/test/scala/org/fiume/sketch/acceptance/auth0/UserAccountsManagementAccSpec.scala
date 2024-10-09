package org.fiume.sketch.acceptance.auth0

import cats.effect.IO
import com.comcast.ip4s.{Host, Port}
import munit.CatsEffectSuite
import org.fiume.sketch.auth0.scripts.UsersScript
import org.fiume.sketch.auth0.scripts.UsersScript.Args
import org.fiume.sketch.shared.auth0.http.{HttpAuthClient, HttpAuthClientConfig}
import org.fiume.sketch.shared.auth0.testkit.PasswordsGens.*
import org.fiume.sketch.shared.auth0.testkit.UserGens.*
import org.fiume.sketch.shared.testkit.Http4sClientContext
import org.fiume.sketch.shared.testkit.syntax.EitherSyntax.*
import org.fiume.sketch.shared.testkit.syntax.OptionSyntax.*

class UserAccountsManagementAccSpec extends CatsEffectSuite with Http4sClientContext:

  private val config = HttpAuthClientConfig(Host.fromString("localhost").someOrFail, Port.fromInt(8080).someOrFail)

  test("soft delete user account"):
    withHttp { http =>
      val username = validUsernames.sample.someOrFail
      val password = validPlainPasswords.sample.someOrFail
      for
        userId <- UsersScript.makeScript().flatMap { _.createUserAccount(Args(username, password, isSuperuser = false)) }
        client = HttpAuthClient.make(config, http)
        jwt <- client.login(username, password).map(_.rightOrFail)

        result <- client.markAccountForDeletion(userId, jwt).map(_.rightOrFail)

        unauthorised <- client.login(username, password)
      yield unauthorised.leftOrFail
    }