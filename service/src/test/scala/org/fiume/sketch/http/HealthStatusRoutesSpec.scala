package org.fiume.sketch.http

import cats.Applicative
import cats.effect.IO
import cats.implicits.*
import munit.CatsEffectSuite
import org.fiume.sketch.shared.app.{ServiceStatus, Version}
import org.fiume.sketch.shared.app.ServiceStatus.{Dependency, DependencyStatus, *}
import org.fiume.sketch.shared.app.ServiceStatus.Dependency.*
import org.fiume.sketch.shared.app.ServiceStatus.json.given
import org.fiume.sketch.shared.app.algebras.{HealthChecker, Versions}
import org.fiume.sketch.shared.app.testkit.VersionGens.versions
import org.fiume.sketch.shared.testkit.EitherSyntax.*
import org.fiume.sketch.shared.testkit.Http4sTestingRoutesDsl
import org.http4s.Method.*
import org.http4s.Status
import org.http4s.client.dsl.io.*
import org.http4s.implicits.*
import org.scalacheck.ShrinkLowPriority

import scala.language.reflectiveCalls

class HealthStatusRoutesSpec
    extends CatsEffectSuite
    with Http4sTestingRoutesDsl
    with VersionsContext
    with HealthCheckContext
    with HealthStatusRoutesSpecContext
    with ShrinkLowPriority:

  test("ping returns pong") {
    for
      routes <- makeHealthStatusRoutes(
        makeVersions(returning = version),
        makeHealthCheck(healthyDatabase),
        makeHealthCheck(healthyRustic)
      )

      result <- send(GET(uri"/ping"))
        .to(routes.router())
        .expectJsonResponseWith(Status.Ok)
//
    yield assertEquals(result.as[String].rightValue, "pong")
  }

  test("healthy status succeeds when dependencies are healthy") {
    for
      routes <- makeHealthStatusRoutes(
        makeVersions(returning = version),
        makeHealthCheck(healthyDatabase),
        makeHealthCheck(healthyRustic)
      )

      result <- send(GET(uri"/status"))
        .to(routes.router())
        .expectJsonResponseWith(Status.Ok)
//
    yield assertEquals(
      result.as[ServiceStatus].rightValue,
      ServiceStatus.make(version, List(healthyDatabase, healthyRustic))
    )
  }

  test("health status fails when at least one dependency is degraded") {
    for
      routes <- makeHealthStatusRoutes(
        makeVersions(returning = version),
        makeHealthCheck(faultyDatabase),
        makeHealthCheck(healthyRustic)
      )

      result <- send(GET(uri"/status"))
        .to(routes.router())
        .expectJsonResponseWith(Status.Ok, debugJsonResponse = true)
//
    yield assertEquals(
      result.as[ServiceStatus].rightValue,
      ServiceStatus.make(version, List(faultyDatabase, healthyRustic))
    )
  }

trait HealthStatusRoutesSpecContext:
  def makeHealthStatusRoutes(
    versions: Versions[IO],
    dbHealthCheck: HealthChecker.DependencyHealthChecker[IO, Database],
    rusticHealthCheck: HealthChecker.DependencyHealthChecker[IO, Rustic]
  ) = IO.delay {
    new HealthStatusRoutes[IO](versions, dbHealthCheck, rusticHealthCheck)
  }

trait VersionsContext:
  val version = versions.sample.get

  def makeVersions[F[_]: Applicative](returning: Version) = new Versions[F]:
    override def currentVersion: F[Version] = returning.pure[F]

trait HealthCheckContext:
  val healthyDatabase = DependencyStatus(database, ServiceStatus.Status.Ok)
  val healthyRustic = DependencyStatus(rustic, ServiceStatus.Status.Ok)
  val faultyDatabase = DependencyStatus(database, ServiceStatus.Status.Degraded)

  def makeHealthCheck[F[_]: Applicative, T <: Dependency](
    dependency: DependencyStatus[T]
  ): HealthChecker.DependencyHealthChecker[F, T] =
    new HealthChecker.DependencyHealthChecker[F, T]:
      override def check(): F[DependencyStatus[T]] = dependency.pure[F]
