package org.fiume.sketch.app

import cats.effect.{Async, Resource}
import cats.effect.syntax.resource.*
import cats.implicits.*
import com.comcast.ip4s.*
import doobie.ConnectionIO
import fs2.io.net.Network
import org.fiume.sketch.auth.accounts.jobs.ScheduledAccountDeletionJob
import org.fiume.sketch.auth.http.{AuthRoutes, UsersRoutes}
import org.fiume.sketch.auth.http.middlewares.Auth0Middleware
import org.fiume.sketch.http.{DocumentsRoutes, HealthStatusRoutes}
import org.fiume.sketch.shared.common.http.middlewares.{SemanticValidationMiddleware, TraceAuditLogMiddleware, WorkerMiddleware}
import org.fiume.sketch.shared.common.jobs.PeriodicJob
import org.http4s.{HttpApp, HttpRoutes}
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.http4s.server.Server
import org.http4s.server.middleware.*
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.{Slf4jFactory, Slf4jLogger}

object Server:

  def run[F[_]: Async: Network](): F[Unit] =
    given LoggerFactory[F] = Slf4jFactory.create[F]
    val logger = Slf4jLogger.getLogger[F]

    val serviceStream = for
      staticConfig <- AppConfig.fromEnvs[F].toResource
      dynamicConfig <- AppConfig.makeDynamicConfig[F, ConnectionIO]().toResource
      resources <- Resources.make(staticConfig)
      httpServiceStream = {
        val server = httpServer[F](staticConfig, resources)
        fs2.Stream
          .resource(server)
          .flatTap { server => fs2.Stream.eval(logger.info(s"Server has started at ${server.address}")) } >>
          fs2.Stream.never
      }.drain

      accountPermanentDeletionStream = PeriodicJob.makeWithDefaultJobErrorHandler(
        interval = staticConfig.account.permanentDeletionJobInterval,
        job = ScheduledAccountDeletionJob.make[F, ConnectionIO](
          resources.accountDeletionEventConsumer,
          resources.accountDeletedNotificationProducer,
          resources.usersStore,
          dynamicConfig
        )
      )

      stream = httpServiceStream
        .concurrently(accountPermanentDeletionStream)
    yield stream

    serviceStream
      .use { _.compile.drain }
      .onError { case ex => logger.error(s"The service has failed with $ex") }

  private def httpServer[F[_]: Async: Network: LoggerFactory](
    config: AppConfig.Static,
    resources: Resources[F]
  ): Resource[F, Server] =
    val server =
      HttpApi.httpApp[F](config, resources).map { httpApp =>
        EmberServerBuilder
          .default[F]
          .withHost(host"0.0.0.0")
          .withPort(config.endpoints.port)
          .withHttpApp(httpApp)
      }
    Resource.suspend(server.map(_.build))

object HttpApi:
  def httpApp[F[_]: Async: LoggerFactory](config: AppConfig.Static, res: Resources[F]): F[HttpApp[F]] =
    val authMiddleware = Auth0Middleware(res.authenticator)

    val authRoutes: HttpRoutes[F] = new AuthRoutes[F](res.authenticator).router()
    val usersRoutes: HttpRoutes[F] = new UsersRoutes[F, ConnectionIO](authMiddleware, res.userManager).router()
    val documentsRoutes: HttpRoutes[F] =
      new DocumentsRoutes[F, ConnectionIO](
        authMiddleware,
        config.documents.documentBytesSizeLimit,
        res.accessControl,
        res.documentsStore
      ).router()
    val healthStatusRoutes: HttpRoutes[F] =
      new HealthStatusRoutes[F](res.versions, res.dbHealthCheck, res.rusticHealthCheck).router()

    val corsMiddleware: CORSPolicy = CORS.policy.withAllowOriginHeader(config.endpoints.cors.allowedOrigins)
    val middlewares = WorkerMiddleware[F](res.customWorkerThreadPool)
      .andThen(SemanticValidationMiddleware.apply)
      .andThen(TraceAuditLogMiddleware[F](config.endpoints.requestResponseLoggingEnabled))
    for
      cAuthRoutes <- corsMiddleware.httpRoutes[F](authRoutes)
      cUsersRoutes <- corsMiddleware.httpRoutes[F](usersRoutes)
      cDocsRoutes <- corsMiddleware.httpRoutes[F](documentsRoutes)
      routes = middlewares(
        healthStatusRoutes <+> cAuthRoutes <+> cUsersRoutes <+> cDocsRoutes
      )
    yield routes.orNotFound
