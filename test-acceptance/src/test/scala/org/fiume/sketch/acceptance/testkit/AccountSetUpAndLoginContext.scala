package org.fiume.sketch.acceptance.testkit

import cats.effect.IO
import com.comcast.ip4s.*
import org.fiume.sketch.auth.scripts.UsersScript
import org.fiume.sketch.auth.scripts.UsersScript.Args
import org.fiume.sketch.shared.auth.Jwt
import org.fiume.sketch.shared.auth.http.HttpAuthClient
import org.fiume.sketch.shared.auth.testkit.PasswordsGens.*
import org.fiume.sketch.shared.auth.testkit.UserGens.*
import org.fiume.sketch.shared.testkit.Http4sClientContext
import org.fiume.sketch.shared.testkit.syntax.EitherSyntax.*
import org.fiume.sketch.shared.testkit.syntax.OptionSyntax.*
import org.scalacheck.Gen

trait AccountSetUpAndLoginContext extends Http4sClientContext:

  private val config = HttpAuthClient.Config(host"localhost", port"8080")

  def loginAndGetAuthenticatedUser(): IO[Jwt] =
    val username = aUsername()
    val password = aPassword()
    for
      script <- UsersScript.makeScript()
      _ <- script.createUserAccount(Args(username, password, globalRole = None))
      jwt <- withHttp { http =>
        HttpAuthClient.make(config, http).login(username, password)
      }
    yield jwt.rightOrFail

  private def aUsername() = Gen.delay(validUsernames).sample.someOrFail
  private def aPassword() = Gen.delay(validPlainPasswords).sample.someOrFail
