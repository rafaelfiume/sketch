package org.fiume.sketch.shared.domain.testkit.syntax

import org.fiume.sketch.shared.app.WithUuid
import org.fiume.sketch.shared.domain.documents.{Document, DocumentId}

import scala.language.adhocExtensions // For testing purposes only, allow Document to be open for extension.

// TODO Make `Syntax` a package `syntax`
object DocumentSyntax:
  extension (document: Document)
    def withUuid(uuid: DocumentId): Document & WithUuid[DocumentId] = Document.make(uuid, document.metadata)
