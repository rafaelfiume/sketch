package org.fiume.sketch.shared.app.http4s.middlewares

import cats.data.{Kleisli, OptionT}
import cats.effect.Async
import cats.implicits.*
import org.http4s.{HttpRoutes, Request}

import scala.concurrent.ExecutionContext

object WorkerMiddleware:
  def apply[F[_]: Async](workerPool: ExecutionContext)(routes: HttpRoutes[F]): HttpRoutes[F] =
    Kleisli { (req: Request[F]) =>
      OptionT(Async[F].evalOn(routes.run(req).value, workerPool))
    }
