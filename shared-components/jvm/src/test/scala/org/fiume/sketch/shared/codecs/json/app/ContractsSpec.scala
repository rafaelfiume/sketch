package org.fiume.sketch.shared.codecs.json.app

import cats.effect.IO
import cats.implicits.*
import io.circe.{Decoder, HCursor}
import io.circe.Decoder.Result
import io.circe.parser.{decode, parse}
import io.circe.syntax.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.fiume.sketch.shared.app.ServiceStatus
import org.fiume.sketch.shared.codecs.json.app.Service.given
import org.fiume.sketch.test.support.EitherSyntax.*
import org.fiume.sketch.test.support.FileContentContext
import org.scalacheck.Gen
import org.scalacheck.effect.PropF.forAllF

import org.fiume.sketch.shared.codecs.json.app.Service
/*
 * Checks encode and decode functions are isomorphic:
 * f . g = id
 * g . f = id
 */
class ContractsSpec extends CatsEffectSuite with ScalaCheckEffectSuite with FileContentContext:

  override def scalaCheckTestParameters = super.scalaCheckTestParameters.withMinSuccessfulTests(3)

  test("encode . decode $ json == json ## status payload") {
    def samples = Gen.oneOf("contract/http/get.status.faulty.json", "contract/http/get.status.healthy.json")
    forAllF(samples) { sample =>
      jsonFrom[IO](sample).use { raw =>
        IO {
          val original = parse(raw).rightValue
          val serviceStatus = decode[ServiceStatus](original.noSpaces).rightValue
          val roundTrip = serviceStatus.asJson
          assertEquals(roundTrip.spaces2SortKeys, original.spaces2SortKeys)
        }
      }
    }
  }