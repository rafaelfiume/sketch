package org.fiume.sketch.shared.auth.http

import cats.effect.{IO, Resource}
import cats.implicits.*
import com.comcast.ip4s.*
import munit.{AnyFixture, CatsEffectSuite}
import org.fiume.sketch.shared.auth.accounts.{AuthenticationError, Jwt}
import org.fiume.sketch.shared.auth.AuthenticationError.*
import org.fiume.sketch.shared.auth.Passwords.PlainPassword
import org.fiume.sketch.shared.auth.User.Username
import org.fiume.sketch.shared.auth.http.model.Login.Error.toErrorInfo
import org.fiume.sketch.shared.auth.http.model.Login.LoginRequestPayload
import org.fiume.sketch.shared.auth.http.model.Login.json.given
import org.fiume.sketch.shared.auth.testkit.{PasswordsGens, UserGens}
import org.fiume.sketch.shared.common.troubleshooting.ErrorInfo.json.given
import org.fiume.sketch.shared.testkit.{Http4sClientContext, HttpServiceContext}
import org.fiume.sketch.shared.testkit.syntax.OptionSyntax.*
import org.http4s.*
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.io.*
import org.http4s.headers.`WWW-Authenticate`

class HttpAuthClientSpec extends HttpAuthClientSpecContext:

  /* Checkout RusticHealthCheckSpec for an auternative way of priming stubbed server responses
   */
  override def munitFixtures: Seq[AnyFixture[?]] = List(httpClient, serverWillReturnError)

  // table test
  List(
    // format: off
    ("user account is not found" ,  unknownUser            , UserNotFoundError),
    ("password is invalid"       ,  userWithInvalidPassword, InvalidPasswordError),
    ("user account is not active",  deactivatedUser        , AccountNotActiveError)
    // format: on
  ).foreach { (description, username, expectedError) =>
    test(s"login returns error when $description"):
      val port = serverWillReturnError()
      val config = HttpClientConfig(host"localhost", port)
      val authClient = HttpAuthClient.make[IO](config, httpClient())
      assertIO(
        authClient.login(username, aPassword()),
        expectedError.asLeft
      )
  }

  test("Internal Server Error causes the client to raise an exception"):
    val port = serverWillReturnError()
    val authClient = HttpAuthClient.make[IO](HttpAuthClient.Config(host"localhost", port), httpClient())
    /*
     * Check the implementation for details on the design decision to raise an exception
     * when receiving unexpected http status codes (5xx) from the server.
     */
    interceptIO[RuntimeException](authClient.login(aUsername(), aPassword()))

trait HttpAuthClientSpecContext extends CatsEffectSuite with Http4sClientContext with HttpServiceContext:
  /*
   * See RusticHealthCheckSpec for an alternative approach to instantiate the client and stub the server.
   */
  val httpClient = ResourceSuiteLocalFixture("httpClient", makeHttpClient())
  val serverWillReturnError = ResourceSuiteLocalFixture("server", primedServer())

  def aUsername(): Username = UserGens.validUsernames.sample.someOrFail
  def aPassword(): PlainPassword = PasswordsGens.validPlainPasswords.sample.someOrFail

  val unknownUser = Username.makeUnsafeFromString("account.not.found")
  val userWithInvalidPassword = Username.makeUnsafeFromString("invalid.password")
  val deactivatedUser = Username.makeUnsafeFromString("inactive.account")

  def primedServer(): Resource[IO, Port] =
    for
      port <- freePort().toResource
      _ <- makeServer(port)(route()).void
    yield port

  private def route(): HttpRoutes[IO] =
    def unauthorised(error: AuthenticationError) =
      Response[IO](status = Status.Unauthorized)
        .putHeaders(`WWW-Authenticate`(Challenge("Bearer", "Authentication Service")))
        .withEntity(error.toErrorInfo)
        .pure[IO]

    HttpRoutes.of[IO] { case req @ POST -> Root / "login" =>
      req.decode { (payload: LoginRequestPayload) =>
        if payload.username == unknownUser.value then unauthorised(UserNotFoundError)
        else if payload.username == userWithInvalidPassword.value then unauthorised(InvalidPasswordError)
        else if payload.username == deactivatedUser.value then unauthorised(AccountNotActiveError)
        else IO.pure(Response(Status.InternalServerError))
      }
    }
