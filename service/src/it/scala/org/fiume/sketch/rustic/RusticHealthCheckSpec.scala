package org.fiume.sketch.rustic

import cats.effect.{IO, Resource}
import cats.implicits.*
import com.comcast.ip4s.{port, *}
import munit.Assertions.*
import munit.CatsEffectSuite
import org.fiume.sketch.shared.app.ServiceStatus.{DependencyStatus, Status}
import org.fiume.sketch.shared.app.ServiceStatus.Dependency.*
import org.fiume.sketch.shared.testkit.{FileContentContext, HttpServiceContext}
import org.fiume.sketch.shared.testkit.syntax.OptionSyntax.*
import org.http4s.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.io.*
import org.http4s.server.Router
import org.scalacheck.Gen

class RusticHealthCheckSpec extends CatsEffectSuite with RusticHealthCheckSpecContext:

  // table test
  List(
    // format: off
    ("healthy"    , healthy,     Status.Ok),
    ("degraded"   , faulty ,     Status.Degraded)
    // format: on
  ).foreach { (label, statusResponse, expectedStatus) =>
    test(s"dependency status is $expectedStatus when rustic service is $label") {
      rusticStatusIs(statusResponse)
        .flatMap { port => RusticHealthCheck.make[IO](config = RusticClientConfig(host, port)) }
        .use { healthCheck =>
          for result <- healthCheck.check()
          yield assertEquals(result, DependencyStatus(rustic, expectedStatus))
        }
    }
  }

  test("dependency status is Degraded when rustic service is Degraded"):
    rusticInDegradedState()
      .flatMap { port => RusticHealthCheck.make[IO](config = RusticClientConfig(host, port)) }
      .use { healthCheck =>
        for result <- healthCheck.check()
        yield assertEquals(result, DependencyStatus(rustic, Status.Degraded))
      }

  test("dependency status is Degraded when rustic service is down"):
    RusticHealthCheck
      .make[IO](config = RusticClientConfig(host, port"3030"))
      .use { healthCheck =>
        for result <- healthCheck.check()
        yield assertEquals(result, DependencyStatus(rustic, Status.Degraded))
      }

trait RusticHealthCheckSpecContext extends FileContentContext with HttpServiceContext:
  val healthy = "status/get.response.healthy.json"
  val faulty = "status/get.response.degraded.json"

  val host = Host.fromString("localhost").getOrElse(throw new AssertionError("localhost is valid host"))

  def rusticStatusIs(pathToResponsePayload: String): Resource[IO, Port] =
    for
      port <- Resource.eval(freePort())
      httpApp <- rusticIsOk(pathToResponsePayload)
      _ <- makeServer(port)(httpApp).void
    yield port

  def rusticInDegradedState(): Resource[IO, Port] =
    for
      port <- Resource.eval(freePort())
      httpApp <- rusticIsDegraded()
      _ <- makeServer(port)(httpApp).void
    yield port

  private def rusticIsOk(pathToResponsePayload: String): Resource[IO, HttpRoutes[IO]] =
    jsonFrom[IO](pathToResponsePayload, debug = false)
      .map { serviceStatus =>
        makeStatusRoute(willRespond = Ok(serviceStatus))
      }

  private def rusticIsDegraded(): Resource[IO, HttpRoutes[IO]] =
    def response: Gen[IO[Response[IO]]] = Gen.oneOf(InternalServerError(), BadRequest())
    Resource.pure { makeStatusRoute(willRespond = response.sample.someOrFail) }

  private def makeStatusRoute(willRespond: IO[Response[IO]]) =
    val route = HttpRoutes.of[IO] { case GET -> Root / "status" => willRespond }
    Router("/" -> route)
