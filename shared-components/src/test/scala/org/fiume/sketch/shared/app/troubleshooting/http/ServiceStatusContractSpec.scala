package org.fiume.sketch.shared.app.troubleshooting.http

import cats.effect.IO
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.fiume.sketch.shared.app.troubleshooting.ServiceStatus
import org.fiume.sketch.shared.app.troubleshooting.http.PayloadCodecs.ServiceStatusCodecs.given
import org.fiume.sketch.shared.testkit.ContractContext
import org.scalacheck.{Gen, ShrinkLowPriority}
import org.scalacheck.effect.PropF.forAllF

class ServiceStatusContractSpec extends CatsEffectSuite with ScalaCheckEffectSuite with ContractContext with ShrinkLowPriority:

  override def scalaCheckTestParameters = super.scalaCheckTestParameters.withMinSuccessfulTests(3)

  test("bijective relationship between encoded and decoded ServiceStatus"):
    def samples =
      Gen.oneOf(
        "contract/shared/app/http/servicestatus.faulty.json",
        "contract/shared/app/http/servicestatus.healthy.json"
      )
    forAllF(samples) { sample =>
      assertBijectiveRelationshipBetweenEncoderAndDecoder[ServiceStatus](sample)
    }
