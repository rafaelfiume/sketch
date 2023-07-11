package org.fiume.sketch.storage.documents

import cats.Eq
import fs2.Stream
import org.fiume.sketch.storage.documents.Model.Metadata.*

import java.time.ZonedDateTime
import java.util.UUID

object Model:
  case class Document[F[_]](
    uuid: UUID,
    metadata: Metadata,
    bytes: Stream[F, Byte],
    createdAt: ZonedDateTime,
    updatedAt: ZonedDateTime
  )

  case class Metadata(name: Name, description: Description)

  object Metadata:
    object Name:
      given Eq[Name] = Eq.fromUniversalEquals
    case class Name(value: String) extends AnyVal // TODO Check invariants: minimum size, supported extensions, etc.
    case class Description(value: String) extends AnyVal
