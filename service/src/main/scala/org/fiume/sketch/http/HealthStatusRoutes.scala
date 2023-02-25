package org.fiume.sketch.http

import cats.MonadThrow
import cats.implicits.*
import org.fiume.sketch.algebras.HealthCheck
import org.fiume.sketch.algebras.{Version, Versions}
import org.fiume.sketch.http.JsonCodecs.AppStatus.given
import org.fiume.sketch.http.Model.AppStatus
import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router

class HealthStatusRoutes[F[_]: MonadThrow](versions: Versions[F], healthCheck: HealthCheck[F]) extends Http4sDsl[F]:
  private val prefix = "/"

  private val httpRoutes: HttpRoutes[F] =
    HttpRoutes.of[F] {
      case (GET | HEAD) -> Root / "ping" =>
        Ok("pong")

      case (GET | HEAD) -> Root / "status" =>
        for
          version <- versions.currentVersion
          db <- healthCheck.healthCheck.as(true).handleError(_ => false)
          resp <- Ok(AppStatus(db, version))
        yield resp
    }

  val routes: HttpRoutes[F] = Router(prefix -> httpRoutes)
