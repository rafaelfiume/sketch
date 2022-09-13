package org.fiume.sketch.http

import cats.Applicative
import cats.effect.IO
import cats.implicits.*
import io.circe.{Decoder, HCursor}
import io.circe.Decoder.Result
import io.circe.parser.decode
import io.circe.syntax.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.fiume.sketch.algebras.HealthCheck
import org.fiume.sketch.app.{Version, Versions}
import org.fiume.sketch.http.HealthStatusRoutes.AppStatus
import org.fiume.sketch.support.{FileContentContext, Http4sTestingRoutesDsl}
import org.fiume.sketch.support.EitherSyntax.*
import org.fiume.sketch.support.gens.Gens.*
import org.http4s.*
import org.http4s.Method.*
import org.http4s.circe.jsonOf
import org.http4s.client.dsl.io.*
import org.http4s.implicits.*
import org.scalacheck.effect.PropF.forAllF

import scala.language.reflectiveCalls

class HealthStatusRoutesSpec
    extends CatsEffectSuite // needed, otherwise non-property-based tests will always pass (i.e. won't be executed)
    with ScalaCheckEffectSuite
    with Http4sTestingRoutesDsl
    with OperationRoutesSpecContext
    with FileContentContext:

  test("ping returns pong") {
    whenSending(GET(uri"/ping"))
      .to(new HealthStatusRoutes[IO](makeVersions(Version("")), makeStore).routes)
      .thenItReturns[String](Status.Ok, withPayload = "\"pong\"") // json string
  }

  test("return the status of the app when db is healthy") {
    forAllF(appVersions) { version =>
      whenSending(GET(uri"/status"))
        .to(
          new HealthStatusRoutes[IO](makeVersions(returning = version), makeStore).routes
        )
        .thenItReturns(
          Status.Ok,
          withPayload = AppStatus(healthy = true, version)
        )
    }
  }

  test("return the status of the app when db unhealthy") {
    forAllF(appVersions) { version =>
      whenSending(GET(uri"/status"))
        .to(
          new HealthStatusRoutes[IO](makeVersions(returning = version), makeStore(IO.raiseError[Unit](DatabaseFailure))).routes
        )
        .thenItReturns(Status.Ok, withPayload = AppStatus(healthy = false, version))
    }
  }

  // <editor-fold desc="Contract test" defaultstate="collapsed">

  test("Testing encoding and decoding of AppStatus") {
    jsonFrom[IO]("contract/http/get.status.json").map { rawJson =>
      val decoded = decode[AppStatus](rawJson).rightValue
      val encoded = decoded.asJson.spaces2

      decode[AppStatus](encoded).rightValue
    }.use_
  }

// </editor-fold>

trait OperationRoutesSpecContext extends VersionsContext with HealthcheckStoreContext:

  given EntityDecoder[IO, AppStatus] = jsonOf[IO, AppStatus]
  given Decoder[AppStatus] = new Decoder[AppStatus] {
    override def apply(c: HCursor): Result[AppStatus] =
      for
        healthy <- c.downField("healthy").as[Boolean]
        appVersion <- c.downField("appVersion").as[String]
      yield AppStatus(healthy, Version(appVersion))
  }

trait VersionsContext:

  def makeVersions[F[_]: Applicative](returning: Version) = new Versions[F] {
    override def currentVersion: F[Version] = returning.pure[F]
  }

case object DatabaseFailure extends Throwable

trait HealthcheckStoreContext:

  def makeStore[F[_]](dbHealthcheck: F[Unit]): HealthCheck[F] = new HealthCheck[F] {
    override def healthCheck: F[Unit] = dbHealthcheck
  }

  def makeStore[F[_]](using F: Applicative[F]): HealthCheck[F] = makeStore(F.unit)
