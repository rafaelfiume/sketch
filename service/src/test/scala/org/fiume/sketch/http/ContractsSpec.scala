package org.fiume.sketch.http

import cats.effect.IO
import cats.implicits.*
import io.circe.{Decoder, HCursor}
import io.circe.Decoder.Result
import io.circe.parser.{decode, parse}
import io.circe.syntax.*
import munit.CatsEffectSuite
import org.fiume.sketch.http.JsonCodecs.AppStatus.given
import org.fiume.sketch.http.Model.AppStatus
import org.fiume.sketch.support.EitherSyntax.*
import org.fiume.sketch.support.FileContentContext

/*
 * Checks encode and decode functions are isomorphic:
 * f . g = id
 * g . f = id
 */
class ContractsSpec extends CatsEffectSuite with FileContentContext:

  test("encode . decode json <-> json (status payload)") {
    jsonFrom[IO]("contract/http/get.status.json").use { raw =>
      IO {
        val original = parse(raw).rightValue
        val appStatus = decode[AppStatus](original.noSpaces).rightValue
        val roundTrip = appStatus.asJson
        assertEquals(roundTrip.spaces2SortKeys, original.spaces2SortKeys)
      }
    }
  }
