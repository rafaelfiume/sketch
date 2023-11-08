package org.fiume.sketch.storage.documents.algebras

import org.fiume.sketch.shared.app.algebras.Store
import org.fiume.sketch.shared.auth0.UserId
import org.fiume.sketch.storage.documents.{Document, DocumentId, DocumentWithId, DocumentWithStream}

trait DocumentsStore[F[_], Txn[_]] extends Store[F, Txn]:

  def store(document: DocumentWithStream[F]): Txn[DocumentId]
  def fetchDocument(uuid: DocumentId): Txn[Option[Document]]
  def documentStream(uuid: DocumentId): Txn[Option[fs2.Stream[F, Byte]]]
  // `fetchAll` will be refined to 'fetch by owner' or 'fetch by workflow' in the future
  def fetchByAuthor(by: UserId): fs2.Stream[F, DocumentWithId]
  def fetchByOwner(by: UserId): fs2.Stream[F, DocumentWithId]
  def fetchAll(): fs2.Stream[F, DocumentWithId]
  def delete(uuid: DocumentId): Txn[Unit]
