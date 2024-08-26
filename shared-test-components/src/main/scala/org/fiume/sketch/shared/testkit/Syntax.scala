package org.fiume.sketch.shared.testkit

import munit.Assertions.fail

import scala.util.Random

object Syntax:

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

  object StringSyntax:
    extension (value: String)
      def _reversed: String = value.reverse
      def _shuffled: String = Random.shuffle(Random.shuffle(value.toList).mkString).mkString
