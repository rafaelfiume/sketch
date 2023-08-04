package org.fiume.sketch.shared.app.http4s.middlewares

import cats.data.{Kleisli, OptionT}
import cats.effect.Async
import cats.implicits.*
import org.fiume.sketch.shared.app.http4s.middlewares.TraceId
import org.http4s.HttpRoutes
import org.http4s.util.CaseInsensitiveString
import org.slf4j.LoggerFactory
import org.typelevel.ci.CIString
import org.typelevel.log4cats.Logger

object TraceAuditLogMiddleware:

  def apply[F[_]: Async](logger: Logger[F], enableLogging: Boolean)(routes: HttpRoutes[F]): HttpRoutes[F] =
    extension (message: String) def info = if enableLogging then OptionT.liftF(logger.info(message)) else OptionT.pure[F](())

    Kleisli { req =>
      val correlationId = req.headers.get(CIString("X-Correlation-ID")).getOrElse(TraceId.make().toString)
      /// val user = req.headers.get(CIString("X-Authenticated-Username")) UUID?? or anonymous
      // TODO Logs seem to be slowing down the app, so we need to find a way to make them async
      s"Received request: ${req.method} ${req.uri} ${req.headers} (correlationId=$correlationId)".info *>
        routes.run(req).flatMap { res =>
          s"Sent response: ${res.status} (correlationId=$correlationId)".info *>
            OptionT.pure(res)
        }
    }

object TraceId:
  def make() = java.util.UUID.randomUUID()