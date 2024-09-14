package org.fiume.sketch.shared.testkit.syntax

import scala.util.Random

object StringSyntax:
  extension (value: String)
    def _reversed: String = value.reverse
    def _shuffled: String = Random.shuffle(Random.shuffle(value.toList).mkString).mkString
