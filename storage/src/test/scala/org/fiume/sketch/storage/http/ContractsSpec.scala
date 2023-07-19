package org.fiume.sketch.storage.http

import munit.CatsEffectSuite
import org.fiume.sketch.shared.test.ContractContext
import org.fiume.sketch.storage.http.JsonCodecs.Incorrects.given
import org.fiume.sketch.storage.http.Model.Incorrect

class ContractsSpec extends CatsEffectSuite with ContractContext:

  // TODO This is a generic response and has to be moved to common lib?
  test("bijective relationship between encoded and decoded Incorrect"):
    assertBijectiveRelationshipBetweenEncoderAndDecoder[Incorrect](
      "contract/http/missing.fields.payload.json"
    )
