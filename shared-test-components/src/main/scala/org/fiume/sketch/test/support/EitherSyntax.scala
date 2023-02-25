package org.fiume.sketch.test.support

import munit.Assertions.fail

object EitherSyntax:

  extension [L, R](e: Either[L, R])
    def leftValue: L =
      e match
        case Left(e)  => e
        case Right(_) => fail(s"expected left value; got <$e> instead")

    def rightValue: R =
      e match
        case Left(_)  => fail(s"expected right value; got <$e> instead")
        case Right(e) => e
