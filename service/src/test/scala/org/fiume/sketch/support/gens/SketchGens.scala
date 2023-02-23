package org.fiume.sketch.support.gens

import fs2.Stream
import org.fiume.sketch.domain.documents.{Document, Metadata}
import org.fiume.sketch.support.gens.Gens.Bytes.*
import org.fiume.sketch.support.gens.Gens.Strings.*
import org.scalacheck.Gen

object SketchGens:

  object Documents:
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
