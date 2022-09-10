package org.fiume.sketch.support

import cats.effect.IO
import munit.Assertions
import org.http4s.{EntityDecoder, HttpRoutes, Request, Status}
import org.http4s.Method.*
import org.http4s.implicits.*

trait Http4sTestingRoutesDsl extends Assertions:

  def whenSending(request: Request[IO]): To = new To(request) {}

  trait To(val request: Request[IO]):
    def to(routes: HttpRoutes[IO]): Then = new Then(routes) {}

    trait Then(val routes: HttpRoutes[IO]):

      def thenItReturns(httpStatus: Status): IO[Unit] =
        routes.orNotFound
          .run(request)
          .flatMap { res => IO(assertEquals(res.status, httpStatus)) }

      def thenItReturns[A](httpStatus: Status, withPayload: A)(implicit ec: EntityDecoder[IO, A]): IO[Unit] =
        routes.orNotFound
          .run(request)
          .flatMap { res =>
            IO(assertEquals(res.status, httpStatus)) *> res.as[A].map(assertEquals(withPayload, _))
          }
