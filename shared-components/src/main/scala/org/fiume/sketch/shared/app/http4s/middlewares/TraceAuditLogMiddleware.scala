package org.fiume.sketch.shared.app.http4s.middlewares

import cats.data.{Kleisli, OptionT}
import cats.effect.Async
import cats.implicits.*
import org.http4s.HttpRoutes
import org.typelevel.ci.CIString
import org.typelevel.log4cats.Logger

object TraceAuditLogMiddleware:

  def apply[F[_]: Async](logger: Logger[F], enableLogging: Boolean)(routes: HttpRoutes[F]): HttpRoutes[F] =
    // logs can have a tremend impact on the service performance
    extension (message: String)
      def info = if enableLogging then OptionT.liftF(logger.info(message)) else OptionT.pure[F](())
      def warn = if enableLogging then OptionT.liftF(logger.warn(message)) else OptionT.pure[F](())

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
