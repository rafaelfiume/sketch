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
import org.fiume.sketch.shared.app.{ServiceHealth, ServiceStatus, Version}
import org.fiume.sketch.shared.app.ServiceHealth.Infra
import org.fiume.sketch.shared.app.algebras.{HealthCheck, Versions}
import org.fiume.sketch.shared.codecs.json.app.Service.given
import org.fiume.sketch.test.support.{FileContentContext, Http4sTestingRoutesDsl}
import org.fiume.sketch.test.support.EitherSyntax.*
import org.fiume.sketch.test.support.Gens.Lists.*
import org.fiume.sketch.test.support.SketchGens.*
import org.http4s.Method.*
import org.http4s.Status
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.client.dsl.io.*
import org.http4s.implicits.*
import org.scalacheck.Gen
import org.scalacheck.effect.PropF.forAllF

import scala.language.reflectiveCalls
// TODO Rename HealthCheckRoutesSpec
class HealthStatusRoutesSpec
    extends CatsEffectSuite // otherwise non-property-based tests won't be executed (?)
    with ScalaCheckEffectSuite
    with Http4sTestingRoutesDsl
    with VersionsContext
    with HealthCheckContext
    with FileContentContext:

  override def scalaCheckTestParameters = super.scalaCheckTestParameters.withMinSuccessfulTests(3)

  test("ping returns pong") {
    forAllF(versions) { version =>
      whenSending(GET(uri"/ping"))
        .to(new HealthStatusRoutes[IO](makeVersions(returning = version), makeHealthCheck()).routes)
        .thenItReturns(Status.Ok, withJsonPayload = "pong")
    }
  }

  test("return healthy status when dependencies are available") {
    def healthyInfras = Gen.const(Infra.Database).map(ServiceHealth.healthy(_))
    forAllF(versions, healthyInfras) { (version, healthy) =>
      whenSending(GET(uri"/status"))
        .to(new HealthStatusRoutes[IO](makeVersions(returning = version), makeHealthCheck(healthy)).routes)
        .thenItReturns(
          Status.Ok,
          withJsonPayload = ServiceStatus(version, healthy)
        )
    }
  }

  test("return faulty status when dependencies are unavailable") {
    def faultyInfras = Gen.const(Infra.Database).map { infra => ServiceHealth.faulty(NonEmptyList.one(infra)) }
    forAllF(versions, faultyInfras) { (version, faulty) =>
      whenSending(GET(uri"/status"))
        .to(
          new HealthStatusRoutes[IO](
            makeVersions(returning = version),
            makeHealthCheck(faulty)
          ).routes
        )
        .thenItReturns(
          Status.Ok,
          withJsonPayload = ServiceStatus(version, faulty),
          debug = true
        )
    }
  }

trait VersionsContext:
  def makeVersions[F[_]: Applicative](returning: Version) = new Versions[F]:
    override def currentVersion: F[Version] = returning.pure[F]

trait HealthCheckContext:
  def makeHealthCheck[F[_]](health: ServiceHealth)(using F: Applicative[F]): HealthCheck[F] = new HealthCheck[F]:
    override def check: F[ServiceHealth] = F.pure(health)

  def makeHealthCheck[F[_]: Applicative](): HealthCheck[F] = makeHealthCheck(ServiceHealth.faulty(Infra.Database))
