package org.fiume.sketch.app

import cats.Monad
import cats.effect.{Async, Concurrent, ExitCode, Resource}
import cats.implicits.*
import com.comcast.ip4s.*
import doobie.ConnectionIO
import fs2.Stream
import org.fiume.sketch.algebras.*
import org.fiume.sketch.datastore.algebras.DocumentsStore
import org.fiume.sketch.datastore.http.DocumentsRoutes
import org.fiume.sketch.datastore.postgres.PostgresStore
import org.fiume.sketch.http.HealthStatusRoutes
import org.http4s.HttpRoutes
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.implicits.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.concurrent.ExecutionContext

object Server:
  def run[F[_]: Async]: F[Unit] =
    (for
      conf <- Resource.eval(ServiceConfig.load[F])
      res <- Resources.make(conf)
    yield res -> conf).use { case res -> conf =>
      for
        log <- Slf4jLogger.create[F]
        _ <- log.info("Starting server")
        versions <- Versions.make[F]
        http = httpd[F](versions, res.store, res.store, log)
        _ <- http.compile.drain.as(ExitCode.Success)
      yield ()
    }

  private def httpd[F[_]: Async](
    versions: Versions[F],
    healthCheck: HealthCheck[F],
    documentStore: DocumentsStore[F, ConnectionIO],
    log: Logger[F]
  ): Stream[F, Unit] =
    val httpApp = HttpApi
      .httpApp[F](
        versions,
        healthCheck,
        documentStore
      )
      .orNotFound
    BlazeServerBuilder[F]
      .bindHttp(host = "0.0.0.0", port = 8080)
      .withHttpApp(httpApp)
      .serve
      .void
      .onError { case ex =>
        Stream.eval(log.error(s"The service has failed with $ex"))
      }

object HttpApi:
  def httpApp[F[_]: Async](
    versions: Versions[F],
    healthCheck: HealthCheck[F],
    documentStore: DocumentsStore[F, ConnectionIO]
  ): HttpRoutes[F] =
    new HealthStatusRoutes[F](versions, healthCheck).routes <+>
     new DocumentsRoutes[F, ConnectionIO](documentStore).routes
