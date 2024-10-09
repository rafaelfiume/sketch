package org.fiume.sketch.shared.auth0.http

import cats.effect.{IO, Resource}
import cats.implicits.*
import com.comcast.ip4s.*
import munit.{AnyFixture, CatsEffectSuite}
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo.json.given
import org.fiume.sketch.shared.auth0.domain.{AuthenticationError, JwtToken}
import org.fiume.sketch.shared.auth0.domain.AuthenticationError.UserNotFoundError
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

  test("login returns error when user account is not found"):
    val port = serverWillReturnError()
    val baseUri = Uri.unsafeFromString(s"http://localhost:$port")
    val authClient = HttpAuthClient.make[IO](httpClient(), baseUri)
    val username = Username.makeUnsafeFromString("account.not.found")

    for result <- authClient.login(username, aPassword())
//
    yield assertEquals(result, Left("Invalid username or password"))

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
        // TODO Semantically invalid username or password
        if payload.username == "account.not.found" then unauthorised(UserNotFoundError)
        else IO.pure(Response(Status.InternalServerError))
      }
    }
