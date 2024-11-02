package org.fiume.sketch.shared.account.management.http

import cats.effect.{IO, Resource}
import cats.implicits.*
import com.comcast.ip4s.*
import munit.CatsEffectSuite
import org.fiume.sketch.shared.account.management.http.model.AccountStateTransitionErrorSyntax.*
import org.fiume.sketch.shared.auth.domain.{Jwt, SoftDeleteAccountError, UserId}
import org.fiume.sketch.shared.auth.domain.SoftDeleteAccountError.*
import org.fiume.sketch.shared.auth.http.model.Users.UserIdVar
import org.fiume.sketch.shared.auth.testkit.JwtGens.*
import org.fiume.sketch.shared.auth.testkit.UserGens.*
import org.fiume.sketch.shared.authorisation.AuthorisationError
import org.fiume.sketch.shared.authorisation.AuthorisationError.*
import org.fiume.sketch.shared.common.troubleshooting.ErrorInfo.json.given
import org.fiume.sketch.shared.testkit.{Http4sClientContext, HttpServiceContext}
import org.fiume.sketch.shared.testkit.syntax.OptionSyntax.someOrFail
import org.http4s.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.io.*
import org.http4s.ember.client.EmberClientBuilder
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

class HttpUsersClientSpec extends HttpUsersClientSpecContext:

  // build quickly to prove out a product feature, but always with a path to a more robust solution.

  // table test
  List(
    // format: off
    ("account is already pending deletion" , AccountAlreadyPendingDeletion),
    ("account is not found"                , SoftDeleteAccountError.AccountNotFound),
    ("authed user is unauthorised"         , UnauthorisedError)
    // format: on
  ).foreach { (description, expectedError) =>
    test(s"marking account for deletion returns error when $description"):
      serverWillReturnError(expectedError).flatMap { makeUsersClient(_) }.use { usersClient =>
        assertIO(
          usersClient.markAccountForDeletion(aUserId(), aJwt()),
          expectedError.asLeft
        )
      }
  }

trait HttpUsersClientSpecContext extends CatsEffectSuite with Http4sClientContext with HttpServiceContext:

  def aUserId(): UserId = userIds.sample.someOrFail
  def aJwt(): Jwt = jwts.sample.someOrFail

  given LoggerFactory[IO] = Slf4jFactory.create[IO]
  def makeUsersClient(port: Port) =
    EmberClientBuilder.default[IO].build.map(HttpUsersClient.make[IO](HttpUsersClient.Config(host"localhost", port), _))

  def serverWillReturnError(error: AuthorisationError | SoftDeleteAccountError): Resource[IO, Port] =
    for
      port <- freePort().toResource
      _ <- makeServer(port)(makeUsersRoute(error)).void
    yield port

  private def makeUsersRoute(error: AuthorisationError | SoftDeleteAccountError): HttpRoutes[IO] =
    HttpRoutes.of[IO] { case req @ DELETE -> Root / "users" / UserIdVar(uuid) =>
      error match
        case e: SoftDeleteAccountError =>
          e match
            case AccountAlreadyPendingDeletion          => Conflict(error.toErrorInfo)
            case SoftDeleteAccountError.AccountNotFound => NotFound(error.toErrorInfo)
        case error: AuthorisationError => Forbidden(error.toErrorInfo)
    }
