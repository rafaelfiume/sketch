package org.fiume.sketch.rustic

import cats.effect.{Async, Resource, Sync}
import cats.implicits.*
import fs2.io.net.Network
import org.fiume.sketch.shared.app.ServiceStatus
import org.fiume.sketch.shared.app.ServiceStatus.{DependencyStatus, Status}
import org.fiume.sketch.shared.app.ServiceStatus.Dependency.*
import org.fiume.sketch.shared.app.ServiceStatus.json.given
import org.fiume.sketch.shared.app.algebras.HealthChecker
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.client.*
import org.http4s.ember.client.*
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

object RusticHealthCheck:
  given [F[_]: Sync]: LoggerFactory[F] = Slf4jFactory.create[F]

  // TODO Change make to take a client as parameter
  def make[F[_]: Async: Network](config: RusticClientConfig): Resource[F, RusticHealthCheck[F]] =
    EmberClientBuilder.default[F].build.map(new RusticHealthCheck(config, _))

private class RusticHealthCheck[F[_]: Async] private (config: RusticClientConfig, client: Client[F])
    extends HealthChecker.DependencyHealthChecker[F, Rustic]:

  override def check(): F[DependencyStatus[Rustic]] =
    client
      .expect[ServiceStatus](config.httpRusticUri / "status")
      .attempt
      .map {
        case Right(s) => DependencyStatus[Rustic](rustic, s.status)
        case Left(_)  => DependencyStatus[Rustic](rustic, Status.Degraded)
      }
