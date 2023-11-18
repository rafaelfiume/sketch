package org.fiume.sketch.profile

import cats.effect.IO
import com.comcast.ip4s.port
import munit.Assertions.*
import munit.CatsEffectSuite
import org.fiume.sketch.profile.ProfileHealthCheck.ProfileServiceConfig
import org.fiume.sketch.shared.app.ServiceStatus.{DependencyStatus, Status}
import org.fiume.sketch.shared.app.ServiceStatus.Dependency.*
import org.fiume.sketch.shared.testkit.FileContentContext
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
    profileStatusIsInWeirdState()
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

trait ProfileHealthCheckSpecContext extends FileContentContext:
  import cats.effect.*, org.http4s.*, org.http4s.dsl.io.*
  import cats.implicits.*
  import com.comcast.ip4s.*
  import org.http4s.ember.server.*
  import org.http4s.implicits.*
  import org.http4s.server.Router
  import org.typelevel.log4cats.LoggerFactory
  import org.typelevel.log4cats.slf4j.Slf4jFactory
  import org.http4s.server.Server
  import org.http4s.circe.CirceEntityEncoder.*

  val localhost = Host.fromString("localhost").getOrElse(throw new AssertionError("localhost is valid host"))

  val healthy = "contract/shared/app/http/servicestatus.healthy.json"
  val faulty = "contract/shared/app/http/servicestatus.faulty.json"

  def profileStatusIs(pathToResponsePayload: String): Resource[IO, Port] =
    for
      port <- Resource.eval(freePort())
      httpApp <- statusRoute(pathToResponsePayload)
      _ <- makeServer(port)(httpApp).void
    yield port

  def profileStatusIsInWeirdState(): Resource[IO, Port] =
    for
      port <- Resource.eval(freePort())
      httpApp <- weirdStatusRoute()
      _ <- makeServer(port)(httpApp).void
    yield port

  private def statusRoute(pathToResponsePayload: String): Resource[IO, HttpRoutes[IO]] =
    jsonFrom[IO](pathToResponsePayload, debug = false).map { serviceStatus =>
      val route = HttpRoutes.of[IO] { case GET -> Root / "status" => Ok(serviceStatus) }
      Router("/" -> route)
    }

  private def weirdStatusRoute(): Resource[IO, HttpRoutes[IO]] =
    def response: Gen[IO[Response[IO]]] = Gen.oneOf(InternalServerError(), BadRequest())
    Resource.pure {
      val route = HttpRoutes.of[IO] { case GET -> Root / "status" => response.sample.get }
      Router("/" -> route)
    }

  given LoggerFactory[IO] = Slf4jFactory.create[IO]
  private def makeServer(port: Port)(httpApp: HttpRoutes[IO]): Resource[IO, Server] =
    EmberServerBuilder
      .default[IO]
      .withHost(ipv4"0.0.0.0")
      .withPort(port)
      .withHttpApp(httpApp.orNotFound)
      .build

  private def freePort(): IO[Port] =
    Resource
      .fromAutoCloseable(IO.delay { new java.net.ServerSocket(0) })
      .use { socket =>
        IO.delay {
          Port.fromInt { socket.getLocalPort() }.getOrElse(throw new AssertionError("there must be a free port"))
        }
      }
