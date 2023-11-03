package org.fiume.sketch.http

import cats.Applicative
import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.implicits.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.fiume.sketch.shared.app.algebras.{HealthCheck, Versions}
import org.fiume.sketch.shared.app.algebras.HealthCheck.ServiceHealth
import org.fiume.sketch.shared.app.algebras.HealthCheck.ServiceHealth.Infra
import org.fiume.sketch.shared.app.algebras.Versions.Version
import org.fiume.sketch.shared.app.troubleshooting.ServiceStatus
import org.fiume.sketch.shared.app.troubleshooting.http.json.ServiceStatusCodecs.given
import org.fiume.sketch.shared.testkit.EitherSyntax.*
import org.fiume.sketch.shared.testkit.Http4sTestingRoutesDsl
import org.fiume.sketch.testkit.SketchGens.given
import org.http4s.Method.*
import org.http4s.Status
import org.http4s.client.dsl.io.*
import org.http4s.implicits.*
import org.scalacheck.{Arbitrary, Gen, ShrinkLowPriority}
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
        healthStatusRoutes <- makeHealthStatusRoutes(makeVersions(returning = version), makeHealthCheck())

        jsonResponse <- send(GET(uri"/ping"))
          .to(healthStatusRoutes.router())
          .expectJsonResponseWith(Status.Ok)
        _ <- IO {
          assertEquals(jsonResponse.as[String].rightValue, "pong")
        }
      yield ()
    }
  }

  test("return healthy status when dependencies are available") {
    given Arbitrary[ServiceHealth] = Arbitrary(Gen.const(Infra.Database).map(ServiceHealth.healthy(_)))
    forAllF { (version: Version, healthy: ServiceHealth) =>
      for
        healthStatusRoutes <- makeHealthStatusRoutes(makeVersions(returning = version), makeHealthCheck(healthy))

        jsonResponse <- send(GET(uri"/status"))
          .to(healthStatusRoutes.router())
          .expectJsonResponseWith(Status.Ok)
        _ <- IO {
          assertEquals(jsonResponse.as[ServiceStatus].rightValue, ServiceStatus(version, healthy))
        }
      yield ()
    }
  }

  test("return faulty status when dependencies are unavailable") {
    given Arbitrary[ServiceHealth] =
      Arbitrary(Gen.const(Infra.Database).map { infra => ServiceHealth.faulty(NonEmptyList.one(infra)) })
    forAllF { (version: Version, faulty: ServiceHealth) =>
      for
        healthStatusRoutes <- makeHealthStatusRoutes(makeVersions(returning = version), makeHealthCheck(faulty))

        jsonResponse <- send(GET(uri"/status"))
          .to(healthStatusRoutes.router())
          .expectJsonResponseWith(Status.Ok)
        _ <- IO {
          assertEquals(jsonResponse.as[ServiceStatus].rightValue, ServiceStatus(version, faulty))
        }
      yield ()
    }
  }

trait HealthStatusRoutesSpecContext:
  def makeHealthStatusRoutes(versions: Versions[IO], healthCheck: HealthCheck[IO]) =
    IO.delay { new HealthStatusRoutes[IO](IORuntime.global.compute, versions, healthCheck) }

trait VersionsContext:
  def makeVersions[F[_]: Applicative](returning: Version) = new Versions[F]:
    override def currentVersion: F[Version] = returning.pure[F]

trait HealthCheckContext:
  def makeHealthCheck[F[_]: Applicative](health: ServiceHealth): HealthCheck[F] = new HealthCheck[F]:
    override def check: F[ServiceHealth] = health.pure[F]

  def makeHealthCheck[F[_]: Applicative](): HealthCheck[F] = makeHealthCheck(ServiceHealth.faulty(Infra.Database))
