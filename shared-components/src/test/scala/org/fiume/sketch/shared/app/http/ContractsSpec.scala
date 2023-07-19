package org.fiume.sketch.shared.app.http

import cats.effect.IO
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.fiume.sketch.shared.app.ServiceStatus
import org.fiume.sketch.shared.app.http.JsonCodecs.ErrorInfoCodecs.given
import org.fiume.sketch.shared.app.http.JsonCodecs.ServiceStatusCodecs.given
import org.fiume.sketch.shared.app.http.Model.ErrorInfo
import org.fiume.sketch.shared.test.ContractContext
import org.scalacheck.{Gen, ShrinkLowPriority}
import org.scalacheck.effect.PropF.forAllF

class ContractsSpec extends CatsEffectSuite with ScalaCheckEffectSuite with ContractContext with ShrinkLowPriority:

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

  test("bijective relationship between encoded and decoded ErrorInfo"):
    def samples = Gen.oneOf(
      "contract/shared/app/http/error.info.json",
      "contract/shared/app/http/error.info.with.details.json"
    )
    forAllF(samples) { sample =>
      assertBijectiveRelationshipBetweenEncoderAndDecoder[ErrorInfo](sample)
    }
