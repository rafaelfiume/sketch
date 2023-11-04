package org.fiume.sketch.storage.testkit

import cats.effect.IO
import fs2.Stream
import org.fiume.sketch.shared.testkit.Gens.Bytes.*
import org.fiume.sketch.shared.testkit.Gens.Strings.*
import org.fiume.sketch.storage.documents.{Document, DocumentUuid, DocumentWithUuid}
import org.fiume.sketch.storage.documents.Document.Metadata
import org.fiume.sketch.storage.documents.Document.Metadata.*
import org.scalacheck.{Arbitrary, Gen}

import java.util.UUID
import scala.util.Random

object DocumentsGens:

  given Arbitrary[Name] = Arbitrary(validNames)
  def validNames: Gen[Name] =
    names.map(Name.notValidatedFromString)
      :| "names"

  def names: Gen[String] = Gen
    .choose(Name.minLength, Name.maxLength)
    .flatMap { namesWithSize(_) }
    :| "name strings"

  def namesWithSize(size: Int): Gen[String] = Gen
    .listOfN(size, nameChars)
    .map(_.mkString)
    :| "name strings"

  def shortNames: Gen[String] =
    Gen
      .choose(0, Name.minLength - 1)
      .flatMap { namesWithSize(_) }
      :| "short names"

  def longNames: Gen[String] =
    Gen
      .choose(Name.maxLength, 100)
      .flatMap { namesWithSize(_) }
      .suchThat(_.length > Name.maxLength)
      :| "long names"

  def namesWithInvalidChars: Gen[String] =
    (for
      names <- names
      invalidChars <- Gen
        .nonEmptyListOf(
          Gen.oneOf("\t", "\n", "\r", "\f", "*", "(", ")", "[", "]", "{", "}", "|", "\\", "'", "\"", "<", ">", "/")
        )
        .map(_.mkString)
    yield Random.shuffle(names ++ invalidChars).mkString) :| "invalid chars"

  given Arbitrary[Description] = Arbitrary(descriptions)
  def descriptions: Gen[Description] = alphaNumString.map(Description.apply)

  given Arbitrary[Metadata] = Arbitrary(metadataG)
  def metadataG: Gen[Metadata] =
    for
      name <- validNames
      description <- descriptions
    yield Metadata(name, description)

  given Arbitrary[Stream[IO, Byte]] = Arbitrary(bytesG)
  def bytesG: Gen[Stream[IO, Byte]] = Gen.nonEmptyListOf(bytes).map(Stream.emits)

  given Arbitrary[Document[IO]] = Arbitrary(documents)
  def documents: Gen[Document[IO]] =
    for
      metadata <- metadataG
      content <- bytesG
    yield Document(metadata, content)

  given Arbitrary[DocumentWithUuid[IO]] = Arbitrary(documentsWithId)
  def documentsWithId: Gen[DocumentWithUuid[IO]] =
    for
      uuid <- Gen.delay(UUID.randomUUID()).map(DocumentUuid(_))
      document <- documents
    yield Document.withUuid(uuid, document.metadata, document.content)

private def nameChars: Gen[Char] =
  Gen.frequency(9 -> Gen.alphaNumChar, 1 -> Gen.const(' '), 1 -> Gen.const('_'), 1 -> Gen.const('-'), 1 -> Gen.const('.'))
