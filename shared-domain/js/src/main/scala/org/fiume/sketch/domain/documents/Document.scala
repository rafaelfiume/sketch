package org.fiume.sketch.domain.documents

import org.fiume.sketch.domain.documents.Metadata

case class Document(metadata: Metadata, bytes: Array[Byte])
