package org.fiume.sketch.storage.documents.http

import cats.effect.IO
import cats.implicits.*
import io.circe.{Decoder, HCursor}
import io.circe.Decoder.Result
import io.circe.parser.{decode, parse}
import io.circe.syntax.*
import munit.CatsEffectSuite
import org.fiume.sketch.shared.test.EitherSyntax.*
import org.fiume.sketch.shared.test.FileContentContext
import org.fiume.sketch.storage.documents.Model.Metadata
import org.fiume.sketch.storage.documents.http.JsonCodecs.given

class ContractsSpec extends CatsEffectSuite with FileContentContext:

  test("encode . decode $ json == json ## document metadata payload") {
    jsonFrom[IO]("contract/documents/document.metadata.json").map { raw =>
      val original = parse(raw).rightValue
      val metadata = decode[Metadata](original.noSpaces).rightValue
      val roundTrip = metadata.asJson
      assertEquals(roundTrip.spaces2SortKeys, original.spaces2SortKeys)
    }.use_
  }
