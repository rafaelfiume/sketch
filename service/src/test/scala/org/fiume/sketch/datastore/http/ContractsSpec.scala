package org.fiume.sketch.datastore.http

import cats.effect.IO
import cats.implicits.*
import io.circe.{Decoder, HCursor}
import io.circe.Decoder.Result
import io.circe.parser.{decode, parse}
import io.circe.syntax.*
import munit.CatsEffectSuite
import org.fiume.sketch.datastore.http.JsonCodecs.Documents.given
import org.fiume.sketch.datastore.http.JsonCodecs.Incorrects.given
import org.fiume.sketch.datastore.http.Model.Incorrect
import org.fiume.sketch.domain.Document
import org.fiume.sketch.support.EitherSyntax.*
import org.fiume.sketch.support.FileContentContext

class ContractsSpec extends CatsEffectSuite with FileContentContext:

  test("decode . encode <-> document metadata payload") {
    jsonFrom[IO]("contract/datasources/http/document.metadata.json").use { raw =>
      IO {
        val original = parse(raw).rightValue
        val metadata = decode[Document.Metadata](original.noSpaces).rightValue
        val roundTrip = metadata.asJson
        assertEquals(roundTrip.spaces2SortKeys, original.spaces2SortKeys)
      }
    }
  }

  // TODO This is a generic response and could be moved to another place?
  test("decode . encode <-> incorrect payload") {
    jsonFrom[IO]("contract/datasources/http/missing.fields.payload.json").use { raw =>
      IO {
        val original = parse(raw).rightValue
        val incorrect = decode[Incorrect](original.noSpaces).rightValue
        val roundTrip = incorrect.asJson
        assertEquals(roundTrip.spaces2SortKeys, original.spaces2SortKeys)
      }
    }
  }
