package org.fiume.sketch.shared.app

import cats.effect.IO
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.fiume.sketch.shared.app.ServiceStatus.json.given
import org.fiume.sketch.shared.testkit.ContractContext
import org.scalacheck.{Gen, ShrinkLowPriority}
import org.scalacheck.effect.PropF.forAllF

class ServiceStatusContractSpec extends CatsEffectSuite with ScalaCheckEffectSuite with ContractContext with ShrinkLowPriority:

  override def scalaCheckTestParameters = super.scalaCheckTestParameters.withMinSuccessfulTests(1)

  test("bijective relationship between encoded and decoded ServiceStatus"):
    def samples =
      Gen.oneOf(
        "service-status/healthy.json",
        "service-status/faulty.json"
      )
    forAllF(samples) { sample =>
      assertBijectiveRelationshipBetweenEncoderAndDecoder[ServiceStatus](sample)
    }