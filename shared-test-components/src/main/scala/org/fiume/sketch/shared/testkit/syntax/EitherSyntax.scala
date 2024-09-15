package org.fiume.sketch.shared.testkit.syntax

import munit.Assertions.fail

object EitherSyntax:
  extension [L, R](e: Either[L, R])
    def leftOrFail: L =
      e match
        case Left(e)  => e
        case Right(_) => fail(s"expected left value; got '$e' instead")

    def rightOrFail: R =
      e match
        case Left(_)  => fail(s"expected right value; got '$e' instead")
        case Right(e) => e
