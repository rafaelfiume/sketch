package org.fiume.sketch.domain

import cats.Eq
import org.fiume.sketch.domain.Document.Metadata
import org.fiume.sketch.domain.Document.Metadata.*

// TODO Duplicated from service module
case class Document(metadata: Metadata, bytes: Array[Byte])

object Document:

  case class Metadata(name: Name, description: Description)

  object Metadata:
    object Name:
      given Eq[Name] = Eq.fromUniversalEquals

    case class Name(value: String) extends AnyVal // TODO Check invariants: minimum size, supported extensions, etc.
    case class Description(value: String) extends AnyVal
