package org.fiume.sketch.domain.documents

import fs2.Stream
import org.fiume.sketch.domain.documents.Metadata

case class Document[F[_]](metadata: Metadata, bytes: Stream[F, Byte])
