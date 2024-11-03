package org.fiume.sketch.acceptance.auth

import cats.effect.IO
import com.comcast.ip4s.*
import munit.CatsEffectSuite
import org.fiume.sketch.auth.scripts.UsersScript
import org.fiume.sketch.auth.scripts.UsersScript.Args
import org.fiume.sketch.shared.account.management.http.HttpUsersClient
import org.fiume.sketch.shared.auth.http.HttpAuthClient
import org.fiume.sketch.shared.auth.testkit.PasswordsGens.*
import org.fiume.sketch.shared.auth.testkit.UserGens.*
import org.fiume.sketch.shared.authorisation.AuthorisationError
import org.fiume.sketch.shared.testkit.Http4sClientContext
import org.fiume.sketch.shared.testkit.syntax.EitherSyntax.*
import org.fiume.sketch.shared.testkit.syntax.OptionSyntax.*

import java.time.Instant

class UserAccountsManagementAccSpec extends CatsEffectSuite with Http4sClientContext:

  test("soft delete user account"):
    withHttp { http =>
      val username = validUsernames.sample.someOrFail
      val password = validPlainPasswords.sample.someOrFail
      for
        userId <- UsersScript.makeScript().flatMap { _.createUserAccount(Args(username, password, globalRole = None)) }
        authClient = HttpAuthClient.make(HttpAuthClient.Config(host"localhost", port"8080"), http)
        jwt <- authClient.login(username, password).map(_.rightOrFail)

        usersClient = HttpUsersClient.make(HttpUsersClient.Config(host"localhost", port"8080"), http)
        result <- usersClient.markAccountForDeletion(userId, jwt).map(_.rightOrFail)

        _ <- IO {
          assertEquals(result.userId, userId)
          assert(result.permanentDeletionAt.isAfter(Instant.now()))
        }
        _ <- assertIO(
          usersClient.restoreAccount(userId, jwt).map(_.leftOrFail),
          AuthorisationError.UnauthorisedError
        )
        unauthorised <- authClient.login(username, password)
      yield unauthorised.leftOrFail
    }
