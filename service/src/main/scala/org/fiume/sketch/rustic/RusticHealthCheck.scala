package org.fiume.sketch.rustic

import cats.effect.Async
import cats.implicits.*
import fs2.io.net.Network
import org.fiume.sketch.shared.common.ServiceStatus
import org.fiume.sketch.shared.common.ServiceStatus.{DependencyStatus, Status}
import org.fiume.sketch.shared.common.ServiceStatus.Dependency.*
import org.fiume.sketch.shared.common.ServiceStatus.json.given
import org.fiume.sketch.shared.common.algebras.HealthChecker
import org.http4s.Uri
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.client.*

object RusticHealthCheck:

  def make[F[_]: Async: Network](config: RusticClientConfig, client: Client[F]): RusticHealthCheck[F] =
    new RusticHealthCheck(config.httpRusticUri, client)

private class RusticHealthCheck[F[_]: Async] private (baseUri: Uri, client: Client[F])
    extends HealthChecker.DependencyHealthChecker[F, Rustic]:

  override def check(): F[DependencyStatus[Rustic]] =
    client
      .expect[ServiceStatus](baseUri / "status")
      .attempt
      .map {
        case Right(s) => DependencyStatus[Rustic](rustic, s.status)
        case Left(_)  => DependencyStatus[Rustic](rustic, Status.Degraded)
      }
