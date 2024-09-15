package org.fiume.sketch.shared.domain.testkit

import cats.effect.IO
import fs2.Stream
import org.fiume.sketch.shared.app.WithUuid
import org.fiume.sketch.shared.domain.documents.{
  Document,
  DocumentId,
  DocumentWithId,
  DocumentWithIdAndStream,
  DocumentWithStream,
  WithStream
}
import org.fiume.sketch.shared.domain.documents.Document.Metadata
import org.fiume.sketch.shared.domain.documents.Document.Metadata.*
import org.fiume.sketch.shared.testkit.Gens.Bytes.*
import org.fiume.sketch.shared.testkit.Gens.Strings.*
import org.scalacheck.{Arbitrary, Gen}

import java.util.UUID
import scala.util.Random

object DocumentsGens:

  given Arbitrary[DocumentId] = Arbitrary(documentsIds)
  def documentsIds: Gen[DocumentId] = Gen.delay(UUID.randomUUID()).map(DocumentId(_)) :| "DocumentId"

  given Arbitrary[Name] = Arbitrary(validNames)
  def validNames: Gen[Name] =
    names.map(Name.makeUnsafeFromString)
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

  given Arbitrary[Document] = Arbitrary(documents)
  def documents: Gen[Document] =
    for metadata <- metadataG
    yield Document(metadata)

  given Arbitrary[DocumentWithId] = Arbitrary(documentsWithId)
  def documentsWithId: Gen[DocumentWithId] =
    for
      id <- documentsIds
      document <- documents
    yield Document.make(id, document.metadata)

  given Arbitrary[DocumentWithStream[IO]] = Arbitrary(documentsWithStream)
  def documentsWithStream: Gen[DocumentWithStream[IO]] =
    for
      metadata <- metadataG
      content <- bytesG
    yield Document.make[IO](content, metadata)

  import scala.language.adhocExtensions
  given Arbitrary[DocumentWithIdAndStream[IO]] = Arbitrary(documentWithIdAndStreams)
  def documentWithIdAndStreams: Gen[DocumentWithIdAndStream[IO]] =
    for
      id <- documentsIds
      metadata <- metadataG
      stream0 <- bytesG
    yield new Document(metadata) with WithUuid[DocumentId] with WithStream[IO]:
      val uuid = id
      val stream = stream0

private def nameChars: Gen[Char] =
  Gen.frequency(9 -> Gen.alphaNumChar, 1 -> Gen.const(' '), 1 -> Gen.const('_'), 1 -> Gen.const('-'), 1 -> Gen.const('.'))
