package org.fiume.sketch.acceptance.auth0

import cats.effect.IO
import munit.CatsEffectSuite
import org.fiume.sketch.auth0.scripts.UsersScript
import org.fiume.sketch.auth0.scripts.UsersScript.Args
import org.fiume.sketch.shared.auth0.http.HttpAuthClient
import org.fiume.sketch.shared.auth0.testkit.PasswordsGens.*
import org.fiume.sketch.shared.auth0.testkit.UserGens.*
import org.fiume.sketch.shared.testkit.Http4sClientContext
import org.fiume.sketch.shared.testkit.syntax.EitherSyntax.*
import org.fiume.sketch.shared.testkit.syntax.OptionSyntax.*
import org.http4s.Uri

class UserAccountsManagementAccSpec extends CatsEffectSuite with Http4sClientContext:

  private val baseUri = Uri.unsafeFromString("http://localhost:8080")

  test("soft delete user account"):
    val username = validUsernames.sample.someOrFail
    val password = validPlainPasswords.sample.someOrFail
    withHttp { http =>
      for
        userId <- UsersScript.makeScript().flatMap { _.createUserAccount(Args(username, password, isSuperuser = false)) }
        client = HttpAuthClient.make(http, baseUri)
        jwt <- client.login(username, password).map(_.rightOrFail)

        _ <- client.markAccountForDeletion(userId, jwt)

        unauthorised <- client.login(username, password)
      yield unauthorised.leftOrFail
    }
