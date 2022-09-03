package org.fiume.sketch.http

import cats.effect.Async
import cats.implicits.*
import io.circe.{Encoder, Json}
import org.fiume.sketch.algebras.HealthCheck
import org.fiume.sketch.app.{Version, Versions}
import org.fiume.sketch.http.HealthStatusRoutes.AppStatus
import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router

class HealthStatusRoutes[F[_]: Async](versions: Versions[F], healthCheck: HealthCheck[F]) extends Http4sDsl[F]:

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

object HealthStatusRoutes {

  case class AppStatus(healthy: Boolean, version: Version)

  given Encoder[AppStatus] = new Encoder[AppStatus] {
    override def apply(a: AppStatus): Json =
      Json.obj(
        "healthy" -> Json.fromBoolean(a.healthy),
        "appVersion" -> Json.fromString(a.version.value)
      )
  }

}
