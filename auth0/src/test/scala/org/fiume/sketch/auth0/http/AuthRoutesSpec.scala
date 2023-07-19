package org.fiume.sketch.auth0.http

import cats.effect.IO
import cats.implicits.*
import io.circe.parser.{decode, parse}
import io.circe.syntax.*
import munit.CatsEffectSuite
import org.fiume.sketch.auth0.http.AuthRoutes.Model.LoginResponse
import org.fiume.sketch.auth0.http.JsonCodecs.given
import org.fiume.sketch.shared.test.EitherSyntax.*
import org.fiume.sketch.shared.test.FileContentContext

class AuthRoutesSpec extends CatsEffectSuite with FileContentContext:

   /*
    * Contracts
    */

  test("isomorphis between decoded and encoded LoginResponse"):
    jsonFrom[IO]("contract/auth0/http/login.success.json").map { raw =>
      val original = parse(raw).rightValue
      val incorrect = decode[LoginResponse](original.noSpaces).rightValue
      val roundTrip = incorrect.asJson
      assertEquals(roundTrip.spaces2SortKeys, original.spaces2SortKeys)
    }.use_
