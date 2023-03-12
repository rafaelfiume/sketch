package org.fiume.sketch.shared.domain.documents

import org.fiume.sketch.shared.domain.documents.Metadata

case class Document(metadata: Metadata, bytes: Array[Byte])
