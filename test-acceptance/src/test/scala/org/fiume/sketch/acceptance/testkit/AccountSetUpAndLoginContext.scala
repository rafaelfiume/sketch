package org.fiume.sketch.acceptance.testkit

import cats.effect.IO
import org.fiume.sketch.auth0.scripts.UsersScript
import org.fiume.sketch.auth0.scripts.UsersScript.Args
import org.fiume.sketch.shared.auth0.domain.JwtToken
import org.fiume.sketch.shared.auth0.http.HttpAuth0Client
import org.fiume.sketch.shared.auth0.testkit.PasswordsGens.*
import org.fiume.sketch.shared.auth0.testkit.UserGens.*
import org.fiume.sketch.shared.testkit.syntax.EitherSyntax.*
import org.fiume.sketch.shared.testkit.syntax.OptionSyntax.*
import org.http4s.Uri
import org.scalacheck.Gen
import org.fiume.sketch.shared.testkit.Http4sClientContext

trait AccountSetUpAndLoginContext extends Http4sClientContext:

  private def aUsername() = Gen.delay(validUsernames).sample.someOrFail
  private def aPassword() = Gen.delay(validPlainPasswords).sample.someOrFail
  private val baseUri = Uri.unsafeFromString("http://localhost:8080")

  def loginAndGetAuthenticatedUser(): IO[JwtToken] =
    val username = aUsername()
    val password = aPassword()
    for
      script <- UsersScript.makeScript()
      _ <- script.createUserAccount(Args(username, password, isSuperuser = false))
      jwt <- withHttp { http =>
        HttpAuth0Client.make(http, baseUri).login(username, password)
      }
    yield jwt.rightOrFail
