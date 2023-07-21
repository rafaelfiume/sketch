package org.fiume.sketch.shared.test

import cats.effect.IO
import cats.implicits.*
import io.circe.{Decoder, Encoder}
import io.circe.parser.{decode, parse}
import io.circe.syntax.*
import munit.Assertions.*
import org.fiume.sketch.shared.test.EitherSyntax.*
import org.fiume.sketch.shared.test.FileContentContext
import org.scalacheck.Gen

/*
 * A note regarding bijective relationship an isomorphism.
 *
 * AFAIK bijective relationship and isomorphism are equivalent for ADTs, but not necessarily for other structures.
 *
 * Roughly, two structures are isomorphic if they have their structure preserved, which includes data and operations.
 * Serialisation might preserve the data but not the operations during encoding and decoding.
 *
 * Bijective relationship is a weaker condition, denoting a one-to-one mapping between two structures, with no loss of information.
 *
 * That said, encoding and decoding of ADTs does seem to form an isomorphism,
 * as the operations are preserved, e.g. equality, immutability.
 */
trait ContractContext extends FileContentContext:

  def assertBijectiveRelationshipBetweenEncoderAndDecoder[A](
    sample: String
  )(using encoder: Encoder[A], decoder: Decoder[A]): IO[Unit] =
    jsonFrom[IO](sample).map { raw =>
      val original = parse(raw).rightValue
      val incorrect = decode[A](original.noSpaces).rightValue
      val roundTrip = incorrect.asJson
      assertEquals(roundTrip.spaces2SortKeys, original.spaces2SortKeys)
    }.use_