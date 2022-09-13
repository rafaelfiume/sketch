package org.fiume.sketch.support.gens

import org.fiume.sketch.domain.Document
import org.fiume.sketch.support.gens.Gens.Bytes.*
import org.fiume.sketch.support.gens.Gens.Strings.*
import org.scalacheck.Gen

object SketchGens:

  object Documents:
    def descriptions: Gen[Document.Metadata.Description] = alphaNumString.map(Document.Metadata.Description.apply)

    def bytesG: Gen[Array[Byte]] = Gen.nonEmptyListOf(bytes).map(_.toArray)

    def metadataG: Gen[Document.Metadata] =
      for
        name <- alphaNumString.map(Document.Metadata.Name.apply)
        description <- descriptions
      yield Document.Metadata(name, description)

    def documents: Gen[Document] =
      for
        metadata <- metadataG
        bytes <- bytesG
      yield Document(metadata, bytes)
