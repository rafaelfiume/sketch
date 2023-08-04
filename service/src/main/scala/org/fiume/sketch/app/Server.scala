package org.fiume.sketch.app

import cats.Monad
import cats.effect.{Async, Concurrent, ExitCode, Resource}
import cats.implicits.*
import com.comcast.ip4s.*
import doobie.ConnectionIO
import org.fiume.sketch.http.HealthStatusRoutes
import org.fiume.sketch.storage.documents.http.DocumentsRoutes
import org.fiume.sketch.storage.documents.postgres.PostgresDocumentsStore
import org.http4s.{AuthedRoutes, HttpRoutes}
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.http4s.server.Server
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.concurrent.ExecutionContext

object Server:

  def run[F[_]](using F: Async[F]): F[ExitCode] =
    val logger = Slf4jLogger.getLogger[F]
    (for
      conf <- Resource.eval(ServiceConfig.load[F])
      resources <- Resources.make(conf)
      server <- httpServer[F](resources)
    yield server)
      .use { server =>
        logger.info(s"Server has started at ${server.address}") >>
          F.never.as(ExitCode.Success)
      }
      .onError { case ex =>
        logger.error(s"The service has failed with $ex")
      }

  private def httpServer[F[_]: Async](resources: Resources[F]): Resource[F, Server] =
    val httpApp = HttpApi.httpApp[F](resources).orNotFound
    EmberServerBuilder
      .default[F]
      .withHost(host"0.0.0.0")
      .withPort(port"8080")
      .withHttpApp(httpApp)
      .build

object HttpApi:
  import org.http4s.server.middleware.*
  import org.http4s.headers.Origin
  import org.http4s.Uri

  def httpApp[F[_]: Async](res: Resources[F]): HttpRoutes[F] =
    val documentsStorageRoute = new DocumentsRoutes[F, ConnectionIO](enableLogging = true)(res.documentsStore).router()
    val healthStatusRoutes = new HealthStatusRoutes[F](res.versions, res.healthCheck).router()

    // TODO pass origin over config
    val corsRoutes = corsPolicy(
      allow = Origin.Host(Uri.Scheme.http, Uri.RegName("localhost"), 5173.some)
    )(documentsStorageRoute)

    healthStatusRoutes <+> corsRoutes

  def corsPolicy[F[_]](allow: Origin.Host) = CORS.policy.withAllowOriginHost(Set(allow))
