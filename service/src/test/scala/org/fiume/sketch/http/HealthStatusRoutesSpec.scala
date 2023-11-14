package org.fiume.sketch.http

import cats.Applicative
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.implicits.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.fiume.sketch.shared.app.ServiceStatus
import org.fiume.sketch.shared.app.ServiceStatus.{Dependency, DependencyStatus, *}
import org.fiume.sketch.shared.app.ServiceStatus.Dependency.*
import org.fiume.sketch.shared.app.ServiceStatus.json.given
import org.fiume.sketch.shared.app.algebras.{HealthCheck, Versions}
import org.fiume.sketch.shared.app.algebras.Versions.Version
import org.fiume.sketch.shared.app.testkit.VersionGens.given
import org.fiume.sketch.shared.testkit.EitherSyntax.*
import org.fiume.sketch.shared.testkit.Http4sTestingRoutesDsl
import org.http4s.Method.*
import org.http4s.Status
import org.http4s.client.dsl.io.*
import org.http4s.implicits.*
import org.scalacheck.ShrinkLowPriority
import org.scalacheck.effect.PropF.forAllF

import scala.language.reflectiveCalls

class HealthStatusRoutesSpec
    extends CatsEffectSuite // otherwise non-property-based tests won't be executed (?)
    with ScalaCheckEffectSuite
    with Http4sTestingRoutesDsl
    with VersionsContext
    with HealthCheckContext
    with HealthStatusRoutesSpecContext
    with ShrinkLowPriority:

  override def scalaCheckTestParameters = super.scalaCheckTestParameters.withMinSuccessfulTests(3)

  test("ping returns pong") {
    forAllF { (version: Version) =>
      for
        healthStatusRoutes <- makeHealthStatusRoutes(makeVersions(returning = version), makeHealthCheck[IO]())

        result <- send(GET(uri"/ping"))
          .to(healthStatusRoutes.router())
          .expectJsonResponseWith(Status.Ok)
        _ <- IO {
          assertEquals(result.as[String].rightValue, "pong")
        }
      yield ()
    }
  }

  // TODO Much improve the tests below

  test("return healthy status when dependencies are available") {
    forAllF { (version: Version) =>
      val healthyDatabase: DependencyStatus[Database] = DependencyStatus(database, ServiceStatus.Status.Ok)
      for
        healthStatusRoutes <- makeHealthStatusRoutes(makeVersions(returning = version), makeHealthCheck(healthyDatabase))

        result <- send(GET(uri"/status"))
          .to(healthStatusRoutes.router())
          .expectJsonResponseWith(Status.Ok)

        _ <- IO {
          assertEquals(result.as[ServiceStatus].rightValue, ServiceStatus.make(version, List(healthyDatabase)))
        }
      yield ()
    }
  }

  test("return faulty status when dependencies are unavailable") {
    forAllF { (version: Version) =>
      val faultyDatabase: DependencyStatus[Database] = DependencyStatus(database, ServiceStatus.Status.Degraded)
      for
        healthStatusRoutes <- makeHealthStatusRoutes(makeVersions(returning = version), makeHealthCheck(faultyDatabase))

        result <- send(GET(uri"/status"))
          .to(healthStatusRoutes.router())
          .expectJsonResponseWith(Status.Ok, debugJsonResponse = true)

        _ <- IO {
          assertEquals(result.as[ServiceStatus].rightValue, ServiceStatus.make(version, List(faultyDatabase)))
        }
      yield ()
    }
  }

trait HealthStatusRoutesSpecContext:
  def makeHealthStatusRoutes(versions: Versions[IO], healthCheck: HealthCheck.DependencyHealth[IO, Database]) =
    IO.delay { new HealthStatusRoutes[IO](IORuntime.global.compute, versions, healthCheck) }

trait VersionsContext:
  def makeVersions[F[_]: Applicative](returning: Version) = new Versions[F]:
    override def currentVersion: F[Version] = returning.pure[F]

trait HealthCheckContext:
  def makeHealthCheck[F[_]: Applicative, T <: Dependency](dependency: DependencyStatus[T]): HealthCheck.DependencyHealth[F, T] =
    new HealthCheck.DependencyHealth[F, T]:
      override def check(): F[DependencyStatus[T]] = dependency.pure[F]

  // TODO this is very unclear - improve it
  def makeHealthCheck[F[_]: Applicative](): HealthCheck.DependencyHealth[F, Database] = makeHealthCheck[F, Database](
    DependencyStatus(database, ServiceStatus.Status.Degraded)
  )
