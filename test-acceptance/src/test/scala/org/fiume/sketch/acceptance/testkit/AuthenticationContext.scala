package org.fiume.sketch.acceptance.testkit

import cats.effect.IO
import org.fiume.sketch.auth0.http.AuthRoutes.Model.LoginResponsePayload
import org.fiume.sketch.auth0.http.AuthRoutes.Model.json.given
import org.fiume.sketch.auth0.scripts.UsersScript.{Args, *}
import org.fiume.sketch.shared.auth0.Passwords.PlainPassword
import org.fiume.sketch.shared.auth0.User
import org.fiume.sketch.shared.auth0.User.Username
import org.fiume.sketch.shared.auth0.testkit.PasswordsGens.*
import org.fiume.sketch.shared.auth0.testkit.UserGens.*
import org.fiume.sketch.shared.testkit.syntax.EitherSyntax.*
import org.fiume.sketch.shared.testkit.syntax.OptionSyntax.*
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.headers.Authorization
import org.scalacheck.Gen

trait AuthenticationContext extends Http4sClientContext:

  private def aUsername() = Gen.delay(validUsernames).sample.someOrFail
  private def aPassword() = Gen.delay(validPlainPasswords).sample.someOrFail
  private def loginRequest(username: Username, password: PlainPassword) =
    "http://localhost:8080/login".post.withEntity(payload(username, password))

  case class AuthenticatedUser(authorization: Authorization)

  def loginAndGetAuthenticatedUser(): IO[AuthenticatedUser] =
    val username = aUsername()
    val password = aPassword()
    for
      script <- makeScript()
      _ <- script.createUserAccount(Args(username, password, isSuperuser = false))
      authorization <- withHttp {
        _.expect[LoginResponsePayload](loginRequest(username, password))
          .map(_.token)
          .map(jwtToken => Authorization.parse(s"Bearer $jwtToken"))
          .map(_.rightOrFail)
      }
    yield AuthenticatedUser(authorization)

  // TODO Improve this somehow....
  private def payload(username: Username, password: PlainPassword): String =
    s"""
       |{
       |  "username": "${username.value}",
       |  "password": "${password.value}"
       |}
      """.stripMargin
