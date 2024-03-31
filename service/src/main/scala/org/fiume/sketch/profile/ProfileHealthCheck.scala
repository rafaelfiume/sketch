package org.fiume.sketch.profile

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

object ProfileHealthCheck:
  given [F[_]: Sync]: LoggerFactory[F] = Slf4jFactory.create[F]

  def make[F[_]: Async: Network](config: ProfileClientConfig): Resource[F, ProfileHealthCheck[F]] =
    EmberClientBuilder.default[F].build.map(new ProfileHealthCheck(config, _))

private class ProfileHealthCheck[F[_]: Async] private (config: ProfileClientConfig, client: Client[F])
    extends HealthChecker.DependencyHealthChecker[F, Profile]:

  override def check(): F[DependencyStatus[Profile]] =
    client
      .expect[ServiceStatus](config.httpProfileUri / "status")
      .attempt
      .map {
        case Right(s) => DependencyStatus[Profile](profile, s.status)
        case Left(_)  => DependencyStatus[Profile](profile, Status.Degraded)
      }
