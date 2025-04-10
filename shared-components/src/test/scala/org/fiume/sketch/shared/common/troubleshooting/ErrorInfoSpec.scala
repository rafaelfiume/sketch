package org.fiume.sketch.shared.common.troubleshooting

import cats.effect.IO
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.fiume.sketch.shared.common.troubleshooting.ErrorInfo.json.given
import org.fiume.sketch.shared.testkit.ContractContext
import org.scalacheck.{Gen, ShrinkLowPriority}
import org.scalacheck.effect.PropF.forAllF

class ErrorInfoSpec extends CatsEffectSuite with ScalaCheckEffectSuite with ContractContext with ShrinkLowPriority:

  override def scalaCheckTestParameters = super.scalaCheckTestParameters.withMinSuccessfulTests(1)

  test("ErrorInfo encode and decode form a bijective relationship"):
    def samples = Gen.oneOf(
      "error-info/error.info.json",
      "error-info/error.info.with.details.json"
    )
    forAllF(samples) { sample =>
      assertBijectiveRelationshipBetweenEncoderAndDecoder[ErrorInfo](sample)
    }
