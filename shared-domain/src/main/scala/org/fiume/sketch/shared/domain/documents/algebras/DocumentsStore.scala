package org.fiume.sketch.shared.domain.documents.algebras

import org.fiume.sketch.shared.auth.UserId
import org.fiume.sketch.shared.common.app.Store
import org.fiume.sketch.shared.domain.documents.{DocumentId, DocumentWithId, DocumentWithStream}

trait DocumentsStore[F[_], Txn[_]] extends Store[F, Txn]:

  def store(document: DocumentWithStream[F]): Txn[DocumentId]
  def fetchDocument(uuid: DocumentId): Txn[Option[DocumentWithId]]
  def documentStream(uuid: DocumentId): fs2.Stream[Txn, Byte]
  def fetchDocuments(uuids: fs2.Stream[Txn, DocumentId]): fs2.Stream[Txn, DocumentWithId]
  def fetchDocumentsByOwnerId(ownerId: UserId): fs2.Stream[Txn, DocumentWithId]
  def delete(uuid: DocumentId): Txn[Option[DocumentId]]
