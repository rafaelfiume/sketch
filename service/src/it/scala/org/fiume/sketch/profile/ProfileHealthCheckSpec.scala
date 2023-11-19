package org.fiume.sketch.profile

import cats.effect.{IO, Resource}
import cats.implicits.*
import com.comcast.ip4s.{port, *}
import munit.Assertions.*
import munit.CatsEffectSuite
import org.fiume.sketch.shared.app.ServiceStatus.{DependencyStatus, Status}
import org.fiume.sketch.shared.app.ServiceStatus.Dependency.*
import org.fiume.sketch.shared.testkit.FileContentContext
import org.fiume.sketch.testkit.HttpServiceContext
import org.http4s.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.io.*
import org.http4s.server.Router
import org.scalacheck.Gen

class ProfileHealthCheckSpec extends CatsEffectSuite with ProfileHealthCheckSpecContext:

  List( // table test
    ("healthy", healthy, Status.Ok),
    ("faulty", faulty, Status.Degraded)
  ).foreach { (label, statusResponse, expectedStatus) =>
    test(s"check if rustic (profile) is $label") {
      profileStatusIs(statusResponse)
        .flatMap { port => ProfileHealthCheck.make[IO](config = ProfileClientConfig(localhost, port)) }
        .use { healthCheck =>
          for
            result <- healthCheck.check()
            _ <- IO { assertEquals(result, DependencyStatus(profile, expectedStatus)) }
          yield ()
        }
    }
  }

  test("check if rustic (profile) is behaving"):
    profileStatusInWeirdState()
      .flatMap { port => ProfileHealthCheck.make[IO](config = ProfileClientConfig(localhost, port)) }
      .use { healthCheck =>
        for
          result <- healthCheck.check()
          _ <- IO { assertEquals(result, DependencyStatus(profile, Status.Degraded)) }
        yield ()
      }

  test("check if rustic (profile) is down"):
    ProfileHealthCheck
      .make[IO](config = ProfileClientConfig(localhost, port"3030"))
      .use { healthCheck =>
        for
          result <- healthCheck.check()
          _ <- IO { assertEquals(result, DependencyStatus(profile, Status.Degraded)) }
        yield ()
      }

trait ProfileHealthCheckSpecContext extends FileContentContext with HttpServiceContext:
  val healthy = "contract/service-status/healthy.json"
  val faulty = "contract/service-status/faulty.json"

  val localhost = Host.fromString("localhost").getOrElse(throw new AssertionError("localhost is valid host"))

  def profileStatusIs(pathToResponsePayload: String): Resource[IO, Port] =
    for
      port <- Resource.eval(freePort())
      httpApp <- runningStatusRoute(pathToResponsePayload)
      _ <- makeServer(port)(httpApp).void
    yield port

  def profileStatusInWeirdState(): Resource[IO, Port] =
    for
      port <- Resource.eval(freePort())
      httpApp <- weirdStatusRoute()
      _ <- makeServer(port)(httpApp).void
    yield port

  private def runningStatusRoute(pathToResponsePayload: String): Resource[IO, HttpRoutes[IO]] =
    jsonFrom[IO](pathToResponsePayload, debug = false)
      .map { serviceStatus =>
        makeStatusRoute(willRespond = Ok(serviceStatus))
      }

  private def weirdStatusRoute(): Resource[IO, HttpRoutes[IO]] =
    def response: Gen[IO[Response[IO]]] = Gen.oneOf(InternalServerError(), BadRequest())
    Resource.pure { makeStatusRoute(willRespond = response.sample.get) }

  private def makeStatusRoute(willRespond: IO[Response[IO]]) =
    val route = HttpRoutes.of[IO] { case GET -> Root / "status" => willRespond }
    Router("/" -> route)
