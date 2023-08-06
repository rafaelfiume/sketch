package org.fiume.sketch.shared.app.troubleshooting.http

import cats.effect.IO
import io.circe.parser.decode
import io.circe.syntax.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.fiume.sketch.shared.app.troubleshooting.{ErrorDetails, ErrorInfo, ErrorMessage}
import org.fiume.sketch.shared.app.troubleshooting.http.PayloadCodecs.ErrorInfoCodecs.given
import org.fiume.sketch.shared.testkit.ContractContext
import org.fiume.sketch.shared.testkit.EitherSyntax.*
import org.scalacheck.{Gen, ShrinkLowPriority}
import org.scalacheck.effect.PropF.forAllF

class ErrorInfoContractSpec extends CatsEffectSuite with ScalaCheckEffectSuite with ContractContext with ShrinkLowPriority:

  override def scalaCheckTestParameters = super.scalaCheckTestParameters.withMinSuccessfulTests(3)

  test("bijective relationship between encoded and decoded ErrorInfo"):
    def samples = Gen.oneOf(
      "contract/shared/app/http/error.info.json",
      "contract/shared/app/http/error.info.with.details.json"
    )
    forAllF(samples) { sample =>
      assertBijectiveRelationshipBetweenEncoderAndDecoder[ErrorInfo](sample)
    }
