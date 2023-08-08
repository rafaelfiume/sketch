package org.fiume.sketch.shared.testkit

import cats.effect.IO
import io.circe.Json
import munit.Assertions
import org.http4s.{HttpRoutes, Request, Status}
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.implicits.*

trait Http4sTestingRoutesDsl extends Assertions:

  def send(request: Request[IO]): To = new To(request) {}

  trait To(val request: Request[IO]):
    def to(routes: HttpRoutes[IO]): Then = new Then(routes) {}

    trait Then(val routes: HttpRoutes[IO]):

      def expectEmptyResponseWith(expected: Status): IO[Unit] =
        routes.orNotFound
          .run(request)
          .flatMap { res => IO(assertEquals(res.status, expected)) }

      def expectJsonResponseWith(expected: Status, debugJsonResponse: Boolean = false): IO[Json] =
        routes.orNotFound
          .run(request)
          .flatTap { res => IO(assertEquals(res.status, expected)) }
          .flatMap { _.as[Json] }
          .flatTap { jsonBody => IO { if debugJsonResponse then debugJson(jsonBody) else () } }

      def expectByteStreamResponseWith(expected: Status): IO[fs2.Stream[IO, Byte]] =
        routes.orNotFound
          .run(request)
          .flatTap { res => IO(assertEquals(res.status, expected)) }
          .map { _.body }

      private def debugJson(json: Json): Unit = println(
        s"""|Note that flag `debugJsonResponse` is enabled, so the response body is printed below:
            |${json.spaces2}
         """.stripMargin
      )
