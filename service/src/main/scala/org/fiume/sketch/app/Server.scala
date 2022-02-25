package org.fiume.sketch.app

import cats.Monad
import cats.effect.{Async, Concurrent, ExitCode, Resource}
import cats.implicits.*
import com.comcast.ip4s.*
import fs2.Stream
import org.http4s.HttpRoutes
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.implicits.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.fiume.sketch.algebras.*
import org.fiume.sketch.http.*
import org.fiume.sketch.postgres.PostgresStore

import scala.concurrent.ExecutionContext

object Server:
  def run[F[_]](using F: Async[F]): F[Unit] =
    (for
      conf <- Resource.eval(ServiceConfig.load[F])
      res <- Resources.make(conf)
    yield res -> conf).use { case res -> conf =>
      for
        log <- Slf4jLogger.create[F]
        _ <- log.info("Starting server")
        versions <- Versions.make[F]
        http = httpd[F](versions, res.store, log)
        _ <- http.compile.drain.as(ExitCode.Success)
      yield ()
    }

  private def httpd[F[_]](
    versions: Versions[F],
    healthCheck: HealthCheck[F],
    log: Logger[F]
  )(using F: Async[F]): Stream[F, Unit] =
    val httpApp = HttpApi.httpApp[F](versions, healthCheck).orNotFound
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
    healthCheck: HealthCheck[F]
  ): HttpRoutes[F] =
    HealthStatusRoutes[F](versions, healthCheck).routes
