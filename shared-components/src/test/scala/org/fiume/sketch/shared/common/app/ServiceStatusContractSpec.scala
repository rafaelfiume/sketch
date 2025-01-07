package org.fiume.sketch.shared.common.app

import cats.effect.IO
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.fiume.sketch.shared.common.app.ServiceStatus.json.given
import org.fiume.sketch.shared.testkit.ContractContext
import org.scalacheck.{Gen, ShrinkLowPriority}
import org.scalacheck.effect.PropF.forAllF

class ServiceStatusContractSpec extends CatsEffectSuite with ScalaCheckEffectSuite with ContractContext with ShrinkLowPriority:

  override def scalaCheckTestParameters = super.scalaCheckTestParameters.withMinSuccessfulTests(1)

  test("ServiceStatus encode and decode form a bijective relationship"):
    def samples =
      Gen.oneOf(
        "status/get.response.degraded.json",
        "status/get.response.healthy.json"
      )
    forAllF(samples) { sample =>
      assertBijectiveRelationshipBetweenEncoderAndDecoder[ServiceStatus](sample)
    }
