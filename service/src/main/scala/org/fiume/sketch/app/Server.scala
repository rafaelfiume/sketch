package org.fiume.sketch.app

import cats.Monad
import cats.effect.{Async, Concurrent, ExitCode, Resource}
import cats.implicits.*
import com.comcast.ip4s.*
import doobie.ConnectionIO
import org.fiume.sketch.algebras.*
import org.fiume.sketch.datastore.algebras.DocumentsStore
import org.fiume.sketch.datastore.http.DocumentsRoutes
import org.fiume.sketch.datastore.postgres.PostgresStore
import org.fiume.sketch.http.HealthStatusRoutes
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
      versions <- Resource.liftK(Versions.make[F])
      server <- httpd[F](versions, res.store, res.store)
    yield server)
      .use { server =>
        logger.info(s"Server has started at ${server.address}") >>
          F.never.as(ExitCode.Success)
      }
      .onError { case ex =>
        logger.error(s"The service has failed with $ex")
      }

  private def httpd[F[_]: Async](
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
  def httpApp[F[_]: Async](
    versions: Versions[F],
    healthCheck: HealthCheck[F],
    documentStore: DocumentsStore[F, ConnectionIO]
  ): HttpRoutes[F] =
    new HealthStatusRoutes[F](versions, healthCheck).routes <+>
      new DocumentsRoutes[F, ConnectionIO](documentStore).routes
