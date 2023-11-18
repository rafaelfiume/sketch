package org.fiume.sketch.profile

import cats.effect.IO
import munit.Assertions.*
import munit.CatsEffectSuite
import org.fiume.sketch.shared.app.ServiceStatus.{DependencyStatus, Status}
import org.fiume.sketch.shared.app.ServiceStatus.Dependency.*
import org.fiume.sketch.shared.testkit.FileContentContext
import org.scalacheck.ShrinkLowPriority

class ProfileHealthCheckSpec extends CatsEffectSuite with ProfileHealthCheckSpecContext with ShrinkLowPriority:

  test("check if rustic (Profile) is healthy"):
    profileStatusIs(healthy)
      .flatMap { _ => ProfileHealthCheck.make[IO]() }
      .use { store =>
        for
          result <- store.check()
          _ <- IO { assertEquals(result, DependencyStatus(profile, Status.Ok)) }
        yield ()
      }

  test("check if rustic (Profile) is faulty"):
    profileStatusIs(faulty)
      .flatMap { _ => ProfileHealthCheck.make[IO]() }
      .use { store =>
        for
          result <- store.check()
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

  val healthy = "contract/shared/app/http/servicestatus.healthy.json"
  val faulty = "contract/shared/app/http/servicestatus.faulty.json"

  def profileStatusIs(pathToResponsePayload: String): Resource[IO, Unit] =
    for
      httpApp <- statusRoute(pathToResponsePayload)
      _ <- makeServer(port"3030")(httpApp).void
    yield ()

  private def statusRoute(pathToResponsePayload: String): Resource[IO, HttpRoutes[IO]] =
    jsonFrom[IO](pathToResponsePayload).map { serviceStatus =>
      val route = HttpRoutes.of[IO] { case GET -> Root / "status" => Ok(serviceStatus) }
      println(serviceStatus) // remove it
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
