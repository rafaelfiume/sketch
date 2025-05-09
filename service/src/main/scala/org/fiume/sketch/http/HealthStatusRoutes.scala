package org.fiume.sketch.http

import cats.effect.Async
import cats.implicits.*
import org.fiume.sketch.shared.common.app.{HealthChecker, ServiceStatus, Versions}
import org.fiume.sketch.shared.common.app.HealthChecker.*
import org.fiume.sketch.shared.common.app.ServiceStatus.Dependency.*
import org.fiume.sketch.shared.common.app.ServiceStatus.json.given
import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router

class HealthStatusRoutes[F[_]: Async](
  versions: Versions[F],
  dbHealthCheck: HealthChecker.DependencyHealthChecker[F, Database],
  rusticHealthCheck: HealthChecker.DependencyHealthChecker[F, Rustic]
) extends Http4sDsl[F]:
  private val prefix = "/"

  def router(): HttpRoutes[F] = Router(
    prefix -> httpRoutes
  )

  private val httpRoutes: HttpRoutes[F] =
    HttpRoutes.of[F] {
      case (GET | HEAD) -> Root / "ping" =>
        Ok("pong")

      case (GET | HEAD) -> Root / "status" =>
        for
          version <- versions.currentVersion
          dbHealth <- dbHealthCheck.check()
          rusticHealth <- rusticHealthCheck.check()
          resp <- Ok(ServiceStatus.make(version, List(dbHealth, rusticHealth)))
        yield resp
    }
