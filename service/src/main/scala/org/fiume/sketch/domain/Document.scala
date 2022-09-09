package org.fiume.sketch.domain

import org.fiume.sketch.domain.Document.Metadata
import org.fiume.sketch.domain.Document.Metadata.*

case class Document(metadata: Metadata, bytes: Array[Byte])

object Document:

  case class Metadata(name: Name, description: Description)

  object Metadata:
    case class Name(value: String) extends AnyVal
    case class Description(value: String) extends AnyVal
