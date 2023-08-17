package org.fiume.sketch.storage.documents.algebras

import org.fiume.sketch.shared.app.algebras.Store
import org.fiume.sketch.storage.documents.{Document, DocumentWithUuid}
import org.fiume.sketch.storage.documents.Document.Metadata

import java.util.UUID

trait DocumentsStore[F[_], Txn[_]] extends Store[F, Txn]:

  def store(document: Document[F]): Txn[UUID]
  def update(document: DocumentWithUuid[F]): Txn[Unit]
  def fetchMetadata(uuid: UUID): Txn[Option[Metadata]]
  def fetchContent(uuid: UUID): Txn[Option[fs2.Stream[F, Byte]]]
  // `fetchAll` will be refined to 'fetch by owner' or 'fetch by workflow' in the future
  def fetchAll(): fs2.Stream[F, DocumentWithUuid[F]]
  def delete(uuid: UUID): Txn[Unit]
