package org.fiume.sketch.shared.auth0.http

import cats.effect.{IO, Resource}
import cats.implicits.*
import com.comcast.ip4s.*
import munit.{AnyFixture, CatsEffectSuite}
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo.json.given
import org.fiume.sketch.shared.auth0.domain.{AuthenticationError, JwtToken}
import org.fiume.sketch.shared.auth0.domain.AuthenticationError.*
import org.fiume.sketch.shared.auth0.domain.Passwords.PlainPassword
import org.fiume.sketch.shared.auth0.domain.User.Username
import org.fiume.sketch.shared.auth0.http.model.Login.LoginRequestPayload
import org.fiume.sketch.shared.auth0.http.model.Login.json.given
import org.fiume.sketch.shared.auth0.testkit.{PasswordsGens, UserGens}
import org.fiume.sketch.shared.testkit.{Http4sClientContext, HttpServiceContext}
import org.fiume.sketch.shared.testkit.syntax.OptionSyntax.*
import org.http4s.*
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.io.*
import org.http4s.headers.`WWW-Authenticate`

class HttpAuthClientSpec extends HttpAuthClientSpecContext:

  override def munitFixtures: Seq[AnyFixture[?]] = List(httpClient, serverWillReturnError)

  // table test
  List(
    // format: off
    ("user account is not found" ,  "account.not.found",  UserNotFoundError),
    ("password is invalid"       ,  "invalid.password",   InvalidPasswordError),
    ("user account is not active",  "inactive.account",   AccountNotActiveError)
    // format: on
  ).foreach { (description, username, expectedError) =>
    test(s"login returns error when $description"):
      val port = serverWillReturnError()
      val config = HttpAuthClientConfig(host"localhost", port)
      val authClient = HttpAuthClient.make[IO](config, httpClient())

      for result <- authClient.login(Username.makeUnsafeFromString(username), aPassword())
//
      yield assertEquals(result, expectedError.asLeft)
  }

  test("Internal Server Error causes the client to raise an exception"):
    val port = serverWillReturnError()
    val config = HttpAuthClientConfig(host"localhost", port)
    val authClient = HttpAuthClient.make[IO](config, httpClient())

    /*
     * Check out the implementation for details on the design decision on
     * raising exception upon unexpect status code.
     */
    interceptIO[RuntimeException](authClient.login(aUsername(), aPassword()))

trait HttpAuthClientSpecContext extends CatsEffectSuite with Http4sClientContext with HttpServiceContext:
  val httpClient = ResourceSuiteLocalFixture("httpClient", makeHttpClient())
  val serverWillReturnError = ResourceSuiteLocalFixture("server", serverWillReturnErrorr())

  def aUsername(): Username = UserGens.validUsernames.sample.someOrFail
  def aPassword(): PlainPassword = PasswordsGens.validPlainPasswords.sample.someOrFail

  def serverWillReturnErrorr(): Resource[IO, Port] =
    for
      port <- Resource.eval(freePort())
      _ <- makeServer(port)(route()).void
    yield port

  private def route(): HttpRoutes[IO] =
    def unauthorised(error: AuthenticationError) =
      Response[IO](status = Status.Unauthorized)
        .putHeaders(`WWW-Authenticate`(Challenge("Bearer", "Authentication Service")))
        .withEntity(
          model.Login.Error.failToLogin(error)
        )
        .pure[IO]

    HttpRoutes.of[IO] { case req @ POST -> Root / "login" =>
      req.decode { (payload: LoginRequestPayload) =>
        if payload.username == "account.not.found" then unauthorised(UserNotFoundError)
        else if payload.username == "invalid.password" then unauthorised(InvalidPasswordError)
        else if payload.username == "inactive.account" then unauthorised(AccountNotActiveError)
        else IO.pure(Response(Status.InternalServerError))
      }
    }
