package org.fiume.sketch.domain

import cats.Eq
//import fs2.Stream
import org.fiume.sketch.domain.Document.Metadata
import org.fiume.sketch.domain.Document.Metadata.*

// TODO Duplicated for now
// No , bytes: Stream[F, Byte] for now
case class Document[F[_]](metadata: Metadata)

object Document:

  case class Metadata(name: Name, description: Description)

  object Metadata:
    object Name:
      given Eq[Name] = Eq.fromUniversalEquals

    case class Name(value: String) extends AnyVal // TODO Check invariants: minimum size, supported extensions, etc.
    case class Description(value: String) extends AnyVal
