package org.fiume.sketch.support

import munit.Assertions.fail

object EitherSyntax {

  implicit class EitherOps[L, R](e: Either[L, R]) {
    def leftValue: L =
      e match {
        case Right(_) => fail(s"expected left value; got <$e> instead")
        case Left(e)  => e
      }

    def rightValue: R =
      e match {
        case Left(_)  => fail(s"expected right value; got <$e> instead")
        case Right(e) => e
      }
  }
}
