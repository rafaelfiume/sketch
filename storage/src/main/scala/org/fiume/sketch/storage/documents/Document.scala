package org.fiume.sketch.storage.documents

import cats.Eq
import fs2.Stream
import org.fiume.sketch.shared.app.WithUuid
import org.fiume.sketch.storage.documents.Document.Metadata
import org.fiume.sketch.storage.documents.Document.Metadata.*

import java.time.ZonedDateTime
import java.util.UUID

type DocumentWithId[F[_]] = Document[F] with WithUuid

case class Document[F[_]](
  metadata: Metadata,
  content: Stream[F, Byte]
)

object Document:
  def withId[F[_]](
    withUuid: UUID,
    metadata: Metadata,
    content: Stream[F, Byte]
  ): Document[F] with WithUuid =
    new Document[F](metadata, content) with WithUuid:
      override val uuid: UUID = withUuid

  case class Metadata(name: Name, description: Description)

  object Metadata:
    object Name:
      given Eq[Name] = Eq.fromUniversalEquals
    case class Name(value: String) extends AnyVal // TODO Check invariants: minimum size, supported extensions, etc.
    case class Description(value: String) extends AnyVal
