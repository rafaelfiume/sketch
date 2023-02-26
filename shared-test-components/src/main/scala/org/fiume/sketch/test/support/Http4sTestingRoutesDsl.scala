package org.fiume.sketch.test.support

import cats.effect.IO
import munit.Assertions
import org.http4s.{EntityDecoder, HttpRoutes, Request, Status}
import org.http4s.Method.*
import org.http4s.implicits.*

trait Http4sTestingRoutesDsl extends Assertions:

  private val breakingContractWarningMessage =
    """
      |If changing an endpoint breaks this test due to a different payload than expected, be careful:
      |there might be a breaking (contract) change.
      |
      |If that's the case, a new version of that endpont is needed with a new contract, so we avoid breaking existing clients.
      |
      |A possible exception to this rule is _only_ adding new fields to the response payload,
      |as opposed to changing (e.g. renaming) or removing at least one of them.
      |There's _still_ a chance contract will be broken with clients implementing a more strict parsing.
  """.stripMargin
  def whenSending(request: Request[IO]): To = new To(request) {}

  trait To(val request: Request[IO]):
    def to(routes: HttpRoutes[IO]): Then = new Then(routes) {}

    trait Then(val routes: HttpRoutes[IO]):

      def thenItReturns(httpStatus: Status): IO[Unit] =
        routes.orNotFound
          .run(request)
          .flatMap { res => IO(assertEquals(res.status, httpStatus)) }

      def thenItReturns[A](httpStatus: Status, withJsonPayload: A)(implicit ec: EntityDecoder[IO, A]): IO[Unit] =
        routes.orNotFound
          .run(request)
          .flatMap { res =>
            IO { assertEquals(res.status, httpStatus) } *>
              res
                .as[A]
                .onError { error => fail(s"$error\n$breakingContractWarningMessage") }
                .map { assertEquals(_, withJsonPayload, clue = breakingContractWarningMessage) }
          }

      def thenItReturns[A](httpStatus: Status, withPayload: fs2.Stream[IO, Byte]): IO[Unit] =
        routes.orNotFound
          .run(request)
          .flatMap { res =>
            IO { assertEquals(res.status, httpStatus) } *>
              IO.both(res.body.compile.toList, withPayload.compile.toList).map { case (obtained, expected) =>
                assertEquals(obtained, expected)
              }
          }
