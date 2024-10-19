package org.fiume.sketch.shared.common.troubleshooting

import cats.Eq
import cats.kernel.laws.discipline.SemigroupTests
import munit.DisciplineSuite
import org.fiume.sketch.shared.common.troubleshooting.ErrorInfo.ErrorDetails
import org.fiume.sketch.shared.common.troubleshooting.ErrorInfo.ErrorDetails.given
import org.scalacheck.{Arbitrary, Gen}

/*
 * Source: https://typelevel.org/cats/typeclasses/lawtesting.html
 */
class ErrorDetailsLawsSpec extends DisciplineSuite:

  checkAll("ErrorDetails.SemigroupLaws", SemigroupTests[ErrorDetails].semigroup)

  given Eq[ErrorDetails] = Eq.fromUniversalEquals

  given Arbitrary[ErrorDetails] = Arbitrary(errorDetailsGen)
  def errorDetailsGen: Gen[ErrorDetails] =
    Gen
      .nonEmptyListOf(Gen.zip(Gen.alphaNumStr, Gen.alphaNumStr))
      .map(ErrorDetails(_*))
