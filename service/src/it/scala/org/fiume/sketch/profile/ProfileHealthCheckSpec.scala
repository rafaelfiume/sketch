package org.fiume.sketch.profile

import cats.effect.{IO, Resource}
import cats.implicits.*
import com.comcast.ip4s.{port, *}
import munit.Assertions.*
import munit.CatsEffectSuite
import org.fiume.sketch.profile.ProfileHealthCheck.ProfileServiceConfig
import org.fiume.sketch.shared.app.ServiceStatus.{DependencyStatus, Status}
import org.fiume.sketch.shared.app.ServiceStatus.Dependency.*
import org.fiume.sketch.shared.testkit.FileContentContext
import org.fiume.sketch.testkit.HttpServiceContext
import org.http4s.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.io.*
import org.http4s.server.Router
import org.scalacheck.{Gen, ShrinkLowPriority}

class ProfileHealthCheckSpec extends CatsEffectSuite with ProfileHealthCheckSpecContext with ShrinkLowPriority:

  test("check if rustic (Profile) is healthy"):
    profileStatusIs(healthy)
      .flatMap { port => ProfileHealthCheck.make[IO](config = ProfileServiceConfig(localhost, port)) }
      .use { healthCheck =>
        for
          result <- healthCheck.check()
          _ <- IO { assertEquals(result, DependencyStatus(profile, Status.Ok)) }
        yield ()
      }

  test("check if rustic (Profile) is faulty"):
    profileStatusIs(faulty)
      .flatMap { port => ProfileHealthCheck.make[IO](config = ProfileServiceConfig(localhost, port)) }
      .use { healthCheck =>
        for
          result <- healthCheck.check()
          _ <- IO { assertEquals(result, DependencyStatus(profile, Status.Degraded)) }
        yield ()
      }

  test("check if rustic (Profile) is behaving"):
    profileStatusInWeirdState()
      .flatMap { port => ProfileHealthCheck.make[IO](config = ProfileServiceConfig(localhost, port)) }
      .use { healthCheck =>
        for
          result <- healthCheck.check()
          _ <- IO { assertEquals(result, DependencyStatus(profile, Status.Degraded)) }
        yield ()
      }

  test("check if rustic (Profile) is down"):
    ProfileHealthCheck
      .make[IO](config = ProfileServiceConfig(localhost, port"3030"))
      .use { healthCheck =>
        for
          result <- healthCheck.check()
          _ <- IO { assertEquals(result, DependencyStatus(profile, Status.Degraded)) }
        yield ()
      }

trait ProfileHealthCheckSpecContext extends FileContentContext with HttpServiceContext:
  val localhost = Host.fromString("localhost").getOrElse(throw new AssertionError("localhost is valid host"))

  val healthy = "contract/shared/app/http/servicestatus.healthy.json"
  val faulty = "contract/shared/app/http/servicestatus.faulty.json"

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
