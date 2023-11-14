package org.fiume.sketch.http

import cats.effect.Async
import cats.implicits.*
import org.fiume.sketch.shared.app.ServiceStatus
import org.fiume.sketch.shared.app.ServiceStatus.Dependency.*
import org.fiume.sketch.shared.app.ServiceStatus.json.given
import org.fiume.sketch.shared.app.algebras.{HealthCheck, Versions}
import org.fiume.sketch.shared.app.algebras.HealthCheck.*
import org.fiume.sketch.shared.app.http4s.middlewares.WorkerMiddleware
import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router

import scala.concurrent.ExecutionContext

class HealthStatusRoutes[F[_]: Async](
  workerPool: ExecutionContext,
  versions: Versions[F],
  dependencyChecker: HealthCheck.DependencyHealth[F, Database]
) extends Http4sDsl[F]:
  private val prefix = "/"

  def router(): HttpRoutes[F] = Router(
    prefix -> WorkerMiddleware(workerPool)(httpRoutes)
  )

  private val httpRoutes: HttpRoutes[F] =
    HttpRoutes.of[F] {
      case (GET | HEAD) -> Root / "ping" =>
        Ok("pong")

      case (GET | HEAD) -> Root / "status" =>
        for
          version <- versions.currentVersion
          health <- dependencyChecker.check()
          resp <- Ok(ServiceStatus.make(version, List(health)))
        yield resp
    }
