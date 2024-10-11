package org.fiume.sketch.shared.app.http4s.middlewares

import cats.data.{Kleisli, OptionT}
import cats.effect.Async
import cats.implicits.*
import org.http4s.HttpRoutes
import org.typelevel.ci.CIString
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.syntax.LoggerInterpolator

object TraceAuditLogMiddleware:

  def apply[F[_]: Async](enableLogging: Boolean)(routes: HttpRoutes[F]): HttpRoutes[F] =
    // logs can have a tremend impact on the service performance
    given Logger[F] = Slf4jLogger.getLogger[F]
    extension (message: String)
      def info = if enableLogging then OptionT.liftF(info"$message") else OptionT.pure[F](())
      def warn = if enableLogging then OptionT.liftF(warn"$message") else OptionT.pure[F](())

    Kleisli { req =>
      val correlationId = req.headers.get(CIString("X-Correlation-ID")).getOrElse(TraceId.make().toString)
      /// val user = req.headers.get(CIString("X-Authenticated-Username")) UUID?? or anonymous
      (for
        _ <- s"Received request: ${req.method} ${req.uri} ${req.headers} (correlationId=$correlationId)".info
        res <- routes.run(req)
        _ <- s"Sent response: ${res.status} (correlationId=$correlationId)".info
      yield res).onError { error =>
        s"Request (correlationId=$correlationId) failed with: ${error.getMessage()}".warn
      }
    }

object TraceId:
  def make() = java.util.UUID.randomUUID()
