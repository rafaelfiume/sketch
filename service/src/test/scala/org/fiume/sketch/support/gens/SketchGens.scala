package org.fiume.sketch.support.gens

import fs2.Stream
import org.fiume.sketch.domain.Document
import org.fiume.sketch.support.gens.Gens.Bytes.*
import org.fiume.sketch.support.gens.Gens.Strings.*
import org.scalacheck.Gen

object SketchGens:

  object Documents:
    def names: Gen[Document.Metadata.Name] = alphaNumString.map(Document.Metadata.Name.apply)

    def descriptions: Gen[Document.Metadata.Description] = alphaNumString.map(Document.Metadata.Description.apply)

    def metadataG: Gen[Document.Metadata] =
      for
        name <- names
        description <- descriptions
      yield Document.Metadata(name, description)

    def bytesG[F[_]]: Gen[Stream[F, Byte]] = Gen.nonEmptyListOf(bytes).map(Stream.emits)

    def documents[F[_]]: Gen[Document[F]] =
      for
        metadata <- metadataG
        bytes <- bytesG[F]
      yield Document(metadata, bytes)
