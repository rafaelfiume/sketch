package org.fiume.sketch.shared.domain.documents.algebras

import org.fiume.sketch.shared.auth.UserId
import org.fiume.sketch.shared.domain.documents.{Document, DocumentId, DocumentWithId}

trait DocumentsStore[Txn[_]]:

  def store(bytes: Array[Byte], document: Document): Txn[DocumentId]
  def fetchDocument(uuid: DocumentId): Txn[Option[DocumentWithId]]
  def documentStream(uuid: DocumentId): fs2.Stream[Txn, Byte]
  def fetchDocuments(uuids: fs2.Stream[Txn, DocumentId]): fs2.Stream[Txn, DocumentWithId]
  def fetchDocumentsByOwnerId(ownerId: UserId): fs2.Stream[Txn, DocumentWithId]
  def delete(uuid: DocumentId): Txn[Option[DocumentId]]
