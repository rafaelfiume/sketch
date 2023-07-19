package org.fiume.sketch.auth0.http

import cats.effect.IO
import cats.implicits.*
import munit.CatsEffectSuite
import org.fiume.sketch.auth0.http.AuthRoutes.Model.LoginResponse
import org.fiume.sketch.auth0.http.JsonCodecs.given
import org.fiume.sketch.shared.test.ContractContext
import org.fiume.sketch.shared.test.EitherSyntax.*
import org.scalacheck.ShrinkLowPriority

class AuthRoutesSpec extends CatsEffectSuite with ContractContext with ShrinkLowPriority:

  /*
   * Contracts
   */

  test("bijective relationship between decoded and encoded LoginResponse"):
    assertBijectiveRelationshipBetweenEncoderAndDecoder[LoginResponse](
      "contract/auth0/http/login.success.json"
    )
