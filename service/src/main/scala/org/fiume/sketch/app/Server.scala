package org.fiume.sketch.app

import cats.Monad
import cats.effect.{Async, Concurrent, ExitCode, Resource}
import cats.implicits.*
import com.comcast.ip4s.*
import doobie.ConnectionIO
import org.fiume.sketch.app.SketchVersions
import org.fiume.sketch.app.SketchVersions.VersionFile
import org.fiume.sketch.http.HealthStatusRoutes
import org.fiume.sketch.shared.app.algebras.{HealthCheck, Versions}
import org.fiume.sketch.storage.documents.algebras.DocumentsStore
import org.fiume.sketch.storage.documents.postgres.PostgresStore
import org.fiume.sketch.storage.http.DocumentsRoutes
import org.http4s.HttpRoutes
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
      res <- Resources.make(conf)
      versions <- SketchVersions.make[F](VersionFile("sketch.version"))
      server <- httpServer[F](versions, res.store, res.store)
    yield server)
      .use { server =>
        logger.info(s"Server has started at ${server.address}") >>
          F.never.as(ExitCode.Success)
      }
      .onError { case ex =>
        logger.error(s"The service has failed with $ex")
      }

  private def httpServer[F[_]: Async](
    versions: Versions[F],
    healthCheck: HealthCheck[F],
    documentStore: DocumentsStore[F, ConnectionIO]
  ): Resource[F, Server] =
    val httpApp = HttpApi
      .httpApp[F](versions, healthCheck, documentStore)
      .orNotFound
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

  def httpApp[F[_]: Async](
    versions: Versions[F],
    healthCheck: HealthCheck[F],
    documentStore: DocumentsStore[F, ConnectionIO]
  ): HttpRoutes[F] =
    val documentsStorageRoute = new DocumentsRoutes[F, ConnectionIO](documentStore).routes
    val healthStatusRoutes = new HealthStatusRoutes[F](versions, healthCheck).routes

    // TODO pass origin over config
    val corsRoutes = corsPolicy(
      allow = Origin.Host(Uri.Scheme.http, Uri.RegName("localhost"), 5173.some)
    )(documentsStorageRoute)

    healthStatusRoutes <+> corsRoutes

  def corsPolicy[F[_]](allow: Origin.Host) = CORS.policy.withAllowOriginHost(Set(allow))
