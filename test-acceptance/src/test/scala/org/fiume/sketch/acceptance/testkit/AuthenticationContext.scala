package org.fiume.sketch.acceptance.testkit

import cats.effect.IO
import org.fiume.sketch.auth0.http.AuthRoutes.Model.Json.given
import org.fiume.sketch.auth0.http.AuthRoutes.Model.LoginResponsePayload
import org.fiume.sketch.auth0.scripts.UsersScript.*
import org.fiume.sketch.shared.auth0.Passwords.PlainPassword
import org.fiume.sketch.shared.auth0.User.Username
import org.fiume.sketch.shared.auth0.testkit.PasswordsGens.*
import org.fiume.sketch.shared.auth0.testkit.UserGens.*
import org.fiume.sketch.shared.testkit.EitherSyntax.*
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.headers.Authorization
import org.scalacheck.Gen

trait AuthenticationContext extends Http4sClientContext:

  private def aUsername() = Gen.delay(validUsernames).sample.get
  private def aPassword() = Gen.delay(validPlainPasswords).sample.get
  private def loginRequest(username: Username, password: PlainPassword) =
    "http://localhost:8080/login".post.withEntity(payload(username, password))

  def loginAndGetAuthenticationHeader(): IO[Authorization] =
    val username = aUsername()
    val password = aPassword()
    makeScript().flatMap { _.registreUser(username, password) } *>
      withHttp {
        _.expect[LoginResponsePayload](loginRequest(username, password))
          .map(_.token)
          .map(jwtToken => Authorization.parse(s"Bearer $jwtToken"))
          .map(_.rightValue)
      }

  // TODO Improve this somehow....
  private def payload(username: Username, password: PlainPassword): String =
    s"""
       |{
       |  "username": "${username.value}",
       |  "password": "${password.value}"
       |}
      """.stripMargin
