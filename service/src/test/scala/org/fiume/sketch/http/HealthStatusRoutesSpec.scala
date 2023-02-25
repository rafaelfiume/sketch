package org.fiume.sketch.http

import cats.Applicative
import cats.effect.IO
import cats.implicits.*
import io.circe.{Decoder, HCursor}
import io.circe.Decoder.Result
import io.circe.parser.decode
import io.circe.syntax.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.fiume.sketch.algebras.{HealthCheck, Version, Versions}
import org.fiume.sketch.http.JsonCodecs.AppStatus.given
import org.fiume.sketch.http.Model.AppStatus
import org.fiume.sketch.support.SketchGens.*
import org.fiume.sketch.test.support.{FileContentContext, Http4sTestingRoutesDsl}
import org.fiume.sketch.test.support.EitherSyntax.*
import org.http4s.Method.*
import org.http4s.Status
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.client.dsl.io.*
import org.http4s.implicits.*
import org.scalacheck.effect.PropF.forAllF

import scala.language.reflectiveCalls

class HealthStatusRoutesSpec
    extends CatsEffectSuite // needed, otherwise non-property-based tests will always pass (i.e. won't be executed)
    with ScalaCheckEffectSuite
    with Http4sTestingRoutesDsl
    with VersionsContext
    with HealthCheckContext
    with FileContentContext:

  test("ping returns pong") {
    whenSending(GET(uri"/ping"))
      .to(new HealthStatusRoutes[IO](makeVersions(Version("")), makeHealthCheck).routes)
      .thenItReturns[String](Status.Ok, withJsonPayload = "pong")
  }

  test("return the status of the app when db is healthy") {
    forAllF(appVersions) { version =>
      whenSending(GET(uri"/status"))
        .to(new HealthStatusRoutes[IO](makeVersions(returning = version), makeHealthCheck).routes)
        .thenItReturns(
          Status.Ok,
          withJsonPayload = AppStatus(healthy = true, version)
        )
    }
  }

  test("return the status of the app when db unhealthy") {
    forAllF(appVersions) { version =>
      whenSending(GET(uri"/status"))
        .to(
          new HealthStatusRoutes[IO](
            makeVersions(returning = version),
            makeHealthCheck(IO.raiseError[Unit](DatabaseFailure))
          ).routes
        )
        .thenItReturns(Status.Ok, withJsonPayload = AppStatus(healthy = false, version))
    }
  }

trait VersionsContext:

  def makeVersions[F[_]: Applicative](returning: Version) = new Versions[F]:
    override def currentVersion: F[Version] = returning.pure[F]

case object DatabaseFailure extends Throwable

trait HealthCheckContext:

  def makeHealthCheck[F[_]](healthy: F[Unit]): HealthCheck[F] = new HealthCheck[F]:
    override def healthCheck: F[Unit] = healthy

  def makeHealthCheck[F[_]](using F: Applicative[F]): HealthCheck[F] = makeHealthCheck(F.unit)
