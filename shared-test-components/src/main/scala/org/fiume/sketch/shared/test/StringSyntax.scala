package org.fiume.sketch.shared.test

import scala.util.Random

object StringSyntax extends StringSyntax

trait StringSyntax:
  extension (value: String)
    def _reversed: String = value.reverse
    def _shuffled: String = Random.shuffle(Random.shuffle(value.toList).mkString).mkString
