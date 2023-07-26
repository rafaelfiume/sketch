package org.fiume.sketch.shared.app.troubleshooting.http

import cats.effect.IO
import io.circe.parser.decode
import io.circe.syntax.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo
import org.fiume.sketch.shared.app.troubleshooting.ErrorInfo.ErrorCode
import org.fiume.sketch.shared.app.troubleshooting.http.JsonCodecs.ErrorInfoCodecs.given
import org.fiume.sketch.shared.test.ContractContext
import org.fiume.sketch.shared.test.EitherSyntax.*
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

  // TODO Table test
  test("encode ErrorCode to JSON string"):
    assertEquals(ErrorCode.InvalidUserCredentials.asJson.noSpaces, "\"INVALID_USER_CREDENTIALS\"")
    assertEquals(ErrorCode.InvalidDocument.asJson.noSpaces, "\"INVALID_DOCUMENT\"")

  test("decode JSON string to ErrorCode"):
    assertEquals(decode[ErrorCode]("\"INVALID_USER_CREDENTIALS\"").rightValue, ErrorCode.InvalidUserCredentials)
    assertEquals(ErrorCode.InvalidDocument.asJson.noSpaces, "\"INVALID_DOCUMENT\"")
