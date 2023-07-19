package org.fiume.sketch.http

import cats.Applicative
import cats.data.NonEmptyList
import cats.effect.IO
import cats.implicits.*
import io.circe.{Decoder, HCursor}
import io.circe.Decoder.Result
import io.circe.parser.decode
import io.circe.syntax.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.fiume.sketch.shared.app.ServiceStatus
import org.fiume.sketch.shared.app.algebras.{HealthCheck, Versions}
import org.fiume.sketch.shared.app.algebras.HealthCheck.ServiceHealth
import org.fiume.sketch.shared.app.algebras.HealthCheck.ServiceHealth.Infra
import org.fiume.sketch.shared.app.algebras.Versions.Version
import org.fiume.sketch.shared.app.http.JsonCodecs.ServiceStatusCodecs.given
import org.fiume.sketch.shared.test.{FileContentContext, Http4sTestingRoutesDsl}
import org.fiume.sketch.shared.test.EitherSyntax.*
import org.fiume.sketch.shared.test.Gens.Lists.*
import org.fiume.sketch.test.support.SketchGens.given
import org.http4s.Method.*
import org.http4s.Status
import org.http4s.circe.CirceEntityDecoder.*
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
    with FileContentContext
    with ShrinkLowPriority:

  override def scalaCheckTestParameters = super.scalaCheckTestParameters.withMinSuccessfulTests(3)

  test("ping returns pong") {
    forAllF { (version: Version) =>
      for
        jsonResponse <- send(GET(uri"/ping"))
          .to(new HealthStatusRoutes[IO](makeVersions(returning = version), makeHealthCheck()).routes)
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
        jsonResponse <- send(GET(uri"/status"))
          .to(new HealthStatusRoutes[IO](makeVersions(returning = version), makeHealthCheck(healthy)).routes)
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
        jsonResponse <- send(GET(uri"/status"))
          .to(
            new HealthStatusRoutes[IO](
              makeVersions(returning = version),
              makeHealthCheck(faulty)
            ).routes
          )
          .expectJsonResponseWith(Status.Ok)
        _ <- IO {
          assertEquals(jsonResponse.as[ServiceStatus].rightValue, ServiceStatus(version, faulty))
        }
      yield ()
    }
  }

trait VersionsContext:
  def makeVersions[F[_]: Applicative](returning: Version) = new Versions[F]:
    override def currentVersion: F[Version] = returning.pure[F]

trait HealthCheckContext:
  def makeHealthCheck[F[_]](health: ServiceHealth)(using F: Applicative[F]): HealthCheck[F] = new HealthCheck[F]:
    override def check: F[ServiceHealth] = F.pure(health)

  def makeHealthCheck[F[_]: Applicative](): HealthCheck[F] = makeHealthCheck(ServiceHealth.faulty(Infra.Database))
