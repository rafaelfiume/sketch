package org.fiume.sketch.test.support

import cats.effect.IO
import fs2.Stream
import org.fiume.sketch.shared.test.Gens.Bytes.*
import org.fiume.sketch.shared.test.Gens.Strings.*
import org.fiume.sketch.storage.documents.{Document, DocumentWithId}
import org.fiume.sketch.storage.documents.Document.Metadata
import org.scalacheck.{Arbitrary, Gen}

import java.util.UUID

object DocumentsGens:

  given Arbitrary[Metadata.Name] = Arbitrary(names)
  def names: Gen[Metadata.Name] = alphaNumString.map(Metadata.Name.apply)

  given Arbitrary[Metadata.Description] = Arbitrary(descriptions)
  def descriptions: Gen[Metadata.Description] = alphaNumString.map(Metadata.Description.apply)

  given Arbitrary[Metadata] = Arbitrary(metadataG)
  def metadataG: Gen[Metadata] =
    for
      name <- names
      description <- descriptions
    yield Metadata(name, description)

  given Arbitrary[Stream[IO, Byte]] = Arbitrary(bytesG[IO])
  def bytesG[F[_]]: Gen[Stream[F, Byte]] = Gen.nonEmptyListOf(bytes).map(Stream.emits)

  def documents[F[_]]: Gen[DocumentWithId[F]] =
    for
      uuid <- Gen.delay(UUID.randomUUID())
      metadata <- metadataG
      bytes <- bytesG[F]
    yield Document.withId(uuid, metadata, bytes)
