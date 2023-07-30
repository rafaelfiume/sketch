package org.fiume.sketch.http

import cats.MonadThrow
import cats.implicits.*
import org.fiume.sketch.shared.app.algebras.{HealthCheck, Versions}
import org.fiume.sketch.shared.app.algebras.HealthCheck.ServiceHealth
import org.fiume.sketch.shared.app.algebras.Versions.Version
import org.fiume.sketch.shared.app.troubleshooting.ServiceStatus
import org.fiume.sketch.shared.app.troubleshooting.http.PayloadCodecs.ServiceStatusCodecs.given
import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router
class HealthStatusRoutes[F[_]: MonadThrow](versions: Versions[F], healthCheck: HealthCheck[F]) extends Http4sDsl[F]:
  private val prefix = "/"

  def router(): HttpRoutes[F] = Router(prefix -> httpRoutes)

  private val httpRoutes: HttpRoutes[F] =
    HttpRoutes.of[F] {
      case (GET | HEAD) -> Root / "ping" =>
        Ok("pong")

      case (GET | HEAD) -> Root / "status" =>
        for
          version <- versions.currentVersion
          health <- healthCheck.check
          resp <- Ok(ServiceStatus(version, health))
        yield resp
    }
