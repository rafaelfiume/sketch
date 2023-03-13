package org.fiume.sketch.shared.domain.documents

import cats.Eq
import org.fiume.sketch.shared.domain.documents.Metadata.*

case class Metadata(name: Name, description: Description)

object Metadata:
  object Name:
    given Eq[Name] = Eq.fromUniversalEquals
  case class Name(value: String) extends AnyVal // TODO Check invariants: minimum size, supported extensions, etc.
  case class Description(value: String) extends AnyVal
