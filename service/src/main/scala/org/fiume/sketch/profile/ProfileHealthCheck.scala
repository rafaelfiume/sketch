package org.fiume.sketch.profile

import cats.effect.{Async, Resource, Sync}
import cats.implicits.*
import com.comcast.ip4s.{Host, Port}
import fs2.io.net.Network
import org.fiume.sketch.profile.ProfileHealthCheck.ProfileServiceConfig
import org.fiume.sketch.shared.app.ServiceStatus
import org.fiume.sketch.shared.app.ServiceStatus.{DependencyStatus, Status}
import org.fiume.sketch.shared.app.ServiceStatus.Dependency.*
import org.fiume.sketch.shared.app.ServiceStatus.json.given
import org.fiume.sketch.shared.app.algebras.HealthCheck
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.client.*
import org.http4s.ember.client.*
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

object ProfileHealthCheck:
  case class ProfileServiceConfig(httpHost: Host, port: Port)

  given [F[_]: Sync]: LoggerFactory[F] = Slf4jFactory.create[F]

  def make[F[_]: Async: Network](config: ProfileServiceConfig): Resource[F, ProfileHealthCheck[F]] =
    EmberClientBuilder.default[F].build.map(new ProfileHealthCheck(config, _))

private class ProfileHealthCheck[F[_]: Async] private (config: ProfileServiceConfig, client: Client[F])
    extends HealthCheck.DependencyHealth[F, Profile]:

  override def check(): F[DependencyStatus[Profile]] =
    client
      .expect[ServiceStatus](s"http://${config.httpHost}:${config.port}/status")
      .map(s => DependencyStatus[Profile](profile, s.status))
      .handleError { e =>
        e match
          case _: java.net.ConnectException => DependencyStatus[Profile](profile, Status.Degraded)
      }
