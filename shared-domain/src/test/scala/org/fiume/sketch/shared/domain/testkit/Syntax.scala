package org.fiume.sketch.shared.domain.testkit

import org.fiume.sketch.shared.app.WithUuid
import org.fiume.sketch.shared.auth0.UserId
import org.fiume.sketch.shared.domain.documents.{Document, DocumentId, DocumentWithIdAndStream, WithStream}

import scala.language.adhocExtensions // For testing purposes only, allow Document to be open for extension.

object Syntax:

  object DocumentSyntax:
    extension (document: Document)
      def withUuid(uuid: DocumentId): Document & WithUuid[DocumentId] = Document.make(uuid, document.metadata)

    extension [F[_]](d: DocumentWithIdAndStream[F])
      def withOwner(userId: UserId): DocumentWithIdAndStream[F] = new Document(d.metadata.copy(owner = userId))
        with WithUuid[DocumentId]
        with WithStream[F]:
        val uuid = d.uuid
        val stream = d.stream
