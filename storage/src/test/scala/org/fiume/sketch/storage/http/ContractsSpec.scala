package org.fiume.sketch.storage.http

import cats.effect.IO
import cats.implicits.*
import io.circe.{Decoder, HCursor}
import io.circe.Decoder.Result
import io.circe.parser.{decode, parse}
import io.circe.syntax.*
import munit.CatsEffectSuite
import org.fiume.sketch.shared.test.EitherSyntax.*
import org.fiume.sketch.shared.test.FileContentContext
import org.fiume.sketch.storage.http.JsonCodecs.Incorrects.given
import org.fiume.sketch.storage.http.Model.Incorrect

class ContractsSpec extends CatsEffectSuite with FileContentContext:

  // TODO This is a generic response and could be moved to another place?
  test("encode . decode $ json <-> json ## incorrect payload") {
    jsonFrom[IO]("contract/http/missing.fields.payload.json").use { raw =>
      IO {
        val original = parse(raw).rightValue
        val incorrect = decode[Incorrect](original.noSpaces).rightValue
        val roundTrip = incorrect.asJson
        assertEquals(roundTrip.spaces2SortKeys, original.spaces2SortKeys)
      }
    }
  }
