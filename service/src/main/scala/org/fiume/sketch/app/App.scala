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
import org.fiume.sketch.users.UserDataDeletionJob
import org.http4s.{HttpApp, HttpRoutes}
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.http4s.server.Server
import org.http4s.server.middleware.*
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.{Slf4jFactory, Slf4jLogger}

import scala.concurrent.duration.*

object App:

  def run[F[_]: Async: Network](): F[Unit] =
    given LoggerFactory[F] = Slf4jFactory.create[F]
    val logger = Slf4jLogger.getLogger[F]

    val serviceStream = for
      staticConfig <- AppConfig.makeFromEnvs[F]().toResource
      dynamicConfig <- AppConfig.makeDynamicConfig[F]()
      comps <- AppComponents.make(staticConfig)
      httpServiceStream = {
        val server = httpServer[F](staticConfig, comps)
        fs2.Stream
          .resource(server)
          .flatTap { server => fs2.Stream.eval(logger.info(s"Server has started at ${server.address}")) } >>
          fs2.Stream.never
      }.drain

      accountPermanentDeletionStream = PeriodicJob.makeWithDefaultJobErrorHandler(
        interval = staticConfig.account.permanentDeletionJobInterval,
        job = ScheduledAccountDeletionJob.make[F, ConnectionIO](
          comps.accountDeletionEventConsumer,
          comps.accountDeletedNotificationProducer,
          comps.usersStore,
          dynamicConfig
        )
      )
      sketchUserDataDeletionStream = PeriodicJob.makeWithDefaultJobErrorHandler(
        /*
         * User's data deletion should have moderate frequency (every minute or hour).
         *
         * A few minutes or even hours of latency won't likely have any impact on the outcome
         * of a user's data deletion.
         */
        // TODO Extract `interval` to a proper place: load it from an env var.
        interval = 1.minute, // consider to increase this interval
        job = UserDataDeletionJob.make[F, ConnectionIO](comps.accountDeletedNotificationConsumer, comps.documentsStore)
      )
      streams = httpServiceStream
        .concurrently(accountPermanentDeletionStream) // Auth
        .concurrently(sketchUserDataDeletionStream) // Sketch
    yield streams

    serviceStream
      .use { _.compile.drain }
      .onError { case ex => logger.error(s"The service has failed with $ex") }

  private def httpServer[F[_]: Async: Network: LoggerFactory](
    config: AppConfig.Static,
    comps: AppComponents[F]
  ): Resource[F, Server] =
    val server =
      HttpApi.httpApp[F](config, comps).map { httpApp =>
        EmberServerBuilder
          .default[F]
          .withHost(host"0.0.0.0")
          .withPort(config.endpoints.port)
          .withHttpApp(httpApp)
      }
    Resource.suspend(server.map(_.build))

object HttpApi:
  def httpApp[F[_]: Async: LoggerFactory](config: AppConfig.Static, comps: AppComponents[F]): F[HttpApp[F]] =
    val authMiddleware = Auth0Middleware(comps.authenticator)

    val authRoutes: HttpRoutes[F] = new AuthRoutes[F](comps.authenticator).router()
    val usersRoutes: HttpRoutes[F] = new UsersRoutes[F, ConnectionIO](authMiddleware, comps.usersManager).router()
    val documentsRoutes: HttpRoutes[F] =
      new DocumentsRoutes[F, ConnectionIO](
        authMiddleware,
        config.documents.documentBytesSizeLimit,
        comps.accessControl,
        comps.documentsStore
      ).router()
    val healthStatusRoutes: HttpRoutes[F] =
      new HealthStatusRoutes[F](comps.versions, comps.dbHealthCheck, comps.rusticHealthCheck).router()

    val corsMiddleware: CORSPolicy = CORS.policy.withAllowOriginHeader(config.endpoints.cors.allowedOrigins)
    val middlewares = WorkerMiddleware[F](comps.customWorkerThreadPool)
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
