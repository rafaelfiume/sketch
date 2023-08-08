package org.fiume.sketch.acceptance

import cats.effect.IO
import cats.implicits.*
import munit.CatsEffectSuite
import org.fiume.sketch.acceptance.testkit.Http4sClientContext
import org.fiume.sketch.shared.auth0.Passwords.PlainPassword
import org.fiume.sketch.shared.auth0.User.Username
import org.fiume.sketch.shared.auth0.testkit.PasswordsGens.*
import org.fiume.sketch.shared.auth0.testkit.UserGens.*
import org.fiume.sketch.auth0.scripts.UsersScript

class LoginSpec extends CatsEffectSuite with Http4sClientContext with LoginSpecContext:

  test("login returns valid token for valid username and password"):
    val username = validUsernames.sample.get
    val password = validPlainPasswords.sample.get
    val loginRequest = "http://localhost:8080/login".post.withEntity(payload(username, password))

    UsersScript.doRegistreUser(username, password) *>
      http { client =>
        client.expect[String](loginRequest).flatMap(IO.println)
      }

trait LoginSpecContext:
  // TODO Improve this somehow....
  def payload(username: Username, password: PlainPassword): String =
    s"""
       |{
       |  "username": "${username.value}",
       |  "password": "${password.value}"
       |}
      """.stripMargin