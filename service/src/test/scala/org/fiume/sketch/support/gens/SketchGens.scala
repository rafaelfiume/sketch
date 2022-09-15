package org.fiume.sketch.support.gens

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

    def bytesG: Gen[Array[Byte]] = Gen.nonEmptyListOf(bytes).map(_.toArray)

    def documents: Gen[Document] =
      for
        metadata <- metadataG
        bytes <- bytesG
      yield Document(metadata, bytes)
