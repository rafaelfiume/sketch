package org.fiume.sketch.storage.documents.algebras

import org.fiume.sketch.shared.app.algebras.Store
import org.fiume.sketch.storage.documents.{Document, DocumentId, DocumentWithId, DocumentWithStream}
import org.fiume.sketch.storage.documents.Document.Metadata

trait DocumentsStore[F[_], Txn[_]] extends Store[F, Txn]:

  def store(document: DocumentWithStream[F]): Txn[DocumentId]
  def fetchMetadata(uuid: DocumentId): Txn[Option[Metadata]]
  def fetchContent(uuid: DocumentId): Txn[Option[fs2.Stream[F, Byte]]]
  // `fetchAll` will be refined to 'fetch by owner' or 'fetch by workflow' in the future
  def fetchAll(): fs2.Stream[F, DocumentWithId]
  def delete(uuid: DocumentId): Txn[Unit]
