package org.fiume.sketch.shared.app.http4s.middlewares

import cats.data.{Kleisli, OptionT}
import cats.effect.Async
import org.http4s.{HttpRoutes, Request}

import scala.concurrent.ExecutionContext

// See https://typelevel.org/cats-effect/docs/thread-model
// See also https://github.com/typelevel/cats-effect/blob/eb231ffb0f5368792957a8f955b5836556b8a3e6/kernel/shared/src/main/scala/cats/effect/kernel/Async.scala#L455 ?
object WorkerMiddleware:
  def apply[F[_]: Async](workerPool: ExecutionContext)(routes: HttpRoutes[F]): HttpRoutes[F] =
    Kleisli { (req: Request[F]) =>
      OptionT(Async[F].evalOn(routes.run(req).value, workerPool))
    }
