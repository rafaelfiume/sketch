package org.fiume.sketch.rustic

import cats.effect.{IO, Resource}
import cats.implicits.*
import com.comcast.ip4s.*
import munit.CatsEffectSuite
import org.fiume.sketch.shared.common.app.ServiceStatus.{DependencyStatus, Status}
import org.fiume.sketch.shared.common.app.ServiceStatus.Dependency.*
import org.fiume.sketch.shared.testkit.{FileContentContext, HttpServiceContext}
import org.fiume.sketch.shared.testkit.syntax.OptionSyntax.*
import org.http4s.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.io.*
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.server.Router
import org.scalacheck.Gen
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

class RusticHealthCheckSpec extends CatsEffectSuite with RusticHealthCheckSpecContext:

  // table test
  List(
    // format: off
    ("healthy"    , healthy,     Status.Ok),
    ("degraded"   , faulty ,     Status.Degraded)
    // format: on
  ).foreach { (label, statusResponse, expectedStatus) =>
    test(s"dependency status is $expectedStatus when rustic service is $label") {
      rusticStatusIs(statusResponse).flatMap { makeRusticHealthCheck(_) }.use { healthCheck =>
        assertIO(healthCheck.check(), DependencyStatus(rustic, expectedStatus))
      }
    }
  }

  test("dependency status is Degraded when rustic service is Degraded"):
    rusticInDegradedState().flatMap { makeRusticHealthCheck(_) }.use { healthCheck =>
      assertIO(healthCheck.check(), DependencyStatus(rustic, Status.Degraded))
    }

  test("dependency status is Degraded when rustic service is down"):
    makeRusticHealthCheck(port"3030").use { healthCheck =>
      assertIO(healthCheck.check(), DependencyStatus(rustic, Status.Degraded))
    }

trait RusticHealthCheckSpecContext extends FileContentContext with HttpServiceContext:
  val healthy = "status/get.response.healthy.json"
  val faulty = "status/get.response.degraded.json"

  /*
   * See HttpAuthClientSpec for an alternative approach to instantiate the client and stub the server.
   */

  given LoggerFactory[IO] = Slf4jFactory.create[IO]
  def makeRusticHealthCheck(port: Port) =
    EmberClientBuilder.default[IO].build.map(RusticHealthCheck.make(RusticClientConfig(host"localhost", port), _))

  def rusticStatusIs(pathToResponsePayload: String): Resource[IO, Port] =
    for
      port <- freePort().toResource
      httpApp <- rusticIsOk(pathToResponsePayload)
      _ <- makeServer(port)(httpApp).void
    yield port

  def rusticInDegradedState(): Resource[IO, Port] =
    for
      port <- freePort().toResource
      httpApp <- rusticIsDegraded()
      _ <- makeServer(port)(httpApp).void
    yield port

  private def rusticIsOk(pathToResponsePayload: String): Resource[IO, HttpRoutes[IO]] =
    jsonFrom[IO](pathToResponsePayload, debug = false).map { serviceStatus =>
      makeStatusRoute(willRespond = Ok(serviceStatus))
    }

  private def rusticIsDegraded(): Resource[IO, HttpRoutes[IO]] =
    def response: Gen[IO[Response[IO]]] = Gen.oneOf(InternalServerError(), BadRequest())
    Resource.pure { makeStatusRoute(willRespond = response.sample.someOrFail) }

  private def makeStatusRoute(willRespond: IO[Response[IO]]) =
    val route = HttpRoutes.of[IO] { case GET -> Root / "status" => willRespond }
    Router("/" -> route)
