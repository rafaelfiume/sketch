package org.fiume.sketch.shared.testkit.syntax

import munit.Assertions.fail

object OptionSyntax:
  extension [A](o: Option[A])
    def someOrFail: A =
      o match
        case Some(value) => value
        case None        => fail("expected some value; got 'None' instead")
