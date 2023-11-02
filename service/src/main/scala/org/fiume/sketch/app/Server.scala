package org.fiume.sketch.app

import cats.effect.{Async, ExitCode, Resource}
import cats.implicits.*
import com.comcast.ip4s.*
import doobie.ConnectionIO
import fs2.io.net.Network
import org.fiume.sketch.auth0.http.AuthRoutes
import org.fiume.sketch.auth0.http.middlewares.Auth0Middleware
import org.fiume.sketch.http.HealthStatusRoutes
import org.fiume.sketch.shared.app.http4s.middlewares.{SemanticValidationMiddleware, TraceAuditLogMiddleware, WorkerMiddleware}
import org.fiume.sketch.storage.documents.http.DocumentsRoutes
import org.http4s.{HttpApp, HttpRoutes}
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.http4s.server.Server
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.{Slf4jFactory, Slf4jLogger}

object Server:

  def run[F[_]: Network]()(using F: Async[F]): F[ExitCode] =
    given LoggerFactory[F] = Slf4jFactory.create[F]
    val logger = Slf4jLogger.getLogger[F]
    (for
      config <- Resource.eval(ServiceConfig.load[F])
      resources <- Resources.make(config)
      server <- httpServer[F](config.endpoints, resources)
    yield server)
      .use { server =>
        logger.info(s"Server has started at ${server.address}") >>
          F.never.as(ExitCode.Success)
      }
      .onError { case ex =>
        logger.error(s"The service has failed with $ex")
      }

  private def httpServer[F[_]: Async: Network: LoggerFactory](
    config: EndpointsConfig,
    resources: Resources[F]
  ): Resource[F, Server] =
    val server =
      HttpApi.httpApp[F](config, resources).map { httpApp =>
        EmberServerBuilder
          .default[F]
          .withHost(host"0.0.0.0")
          .withPort(config.port)
          .withHttpApp(httpApp)
      }
    Resource.suspend(server.map(_.build))

object HttpApi:
  import org.http4s.server.middleware.*
  import org.http4s.headers.Origin
  import org.http4s.Uri

  def httpApp[F[_]: Async: LoggerFactory](config: EndpointsConfig, res: Resources[F]): F[HttpApp[F]] =
    val authMiddleware = Auth0Middleware(res.authenticator)

    val authRoutes: HttpRoutes[F] = new AuthRoutes[F](res.authenticator).router()
    val documentsRoutes: HttpRoutes[F] =
      new DocumentsRoutes[F, ConnectionIO](authMiddleware, config.documents.documentBytesSizeLimit, res.documentsStore).router()
    val healthStatusRoutes: HttpRoutes[F] =
      new HealthStatusRoutes[F](res.customWorkerThreadPool, res.versions, res.healthCheck).router()

    val corsMiddleware: CORSPolicy =
      CORS.policy.withAllowOriginHost(
        Set(
          Origin.Host(Uri.Scheme.http, Uri.RegName("localhost"), 5173.some),
          Origin.Host(Uri.Scheme.http, Uri.RegName("localhost"), 8181.some)
        )
      )
    val middlewares = WorkerMiddleware[F](res.customWorkerThreadPool)
      .andThen(SemanticValidationMiddleware.apply)
      .andThen(TraceAuditLogMiddleware[F](Slf4jLogger.getLogger[F], config.requestResponseLoggingEnabled))
    for
      cAuthRoutes <- corsMiddleware.httpRoutes[F](authRoutes)
      cDocsRoutes <- corsMiddleware.httpRoutes[F](documentsRoutes)
      routes = middlewares(
        healthStatusRoutes <+> cAuthRoutes <+> cDocsRoutes
      )
    yield routes.orNotFound
