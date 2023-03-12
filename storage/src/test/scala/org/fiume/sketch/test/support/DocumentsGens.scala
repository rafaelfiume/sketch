package org.fiume.sketch.test.support

import fs2.Stream
import org.fiume.sketch.shared.domain.documents.{Document, Metadata}
import org.fiume.sketch.test.support.Gens.Bytes.*
import org.fiume.sketch.test.support.Gens.Strings.*
import org.scalacheck.Gen

object DocumentsGens: // good candidate to be moved to a specific test-components module/lib

  def names: Gen[Metadata.Name] = alphaNumString.map(Metadata.Name.apply)

  def descriptions: Gen[Metadata.Description] = alphaNumString.map(Metadata.Description.apply)

  def metadataG: Gen[Metadata] =
    for
      name <- names
      description <- descriptions
    yield Metadata(name, description)

  def bytesG[F[_]]: Gen[Stream[F, Byte]] = Gen.nonEmptyListOf(bytes).map(Stream.emits)

  def documents[F[_]]: Gen[Document[F]] =
    for
      metadata <- metadataG
      bytes <- bytesG[F]
    yield Document(metadata, bytes)
