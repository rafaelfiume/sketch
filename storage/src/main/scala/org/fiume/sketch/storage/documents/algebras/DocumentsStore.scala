package org.fiume.sketch.storage.documents.algebras

import fs2.Stream
import org.fiume.sketch.shared.app.algebras.Store
import org.fiume.sketch.storage.documents.{Document, DocumentWithId}
import org.fiume.sketch.storage.documents.Document.Metadata

import java.util.UUID

trait DocumentsStore[F[_], Txn[_]] extends Store[F, Txn]:

  def store(document: Document[F]): Txn[UUID]
  def update(document: DocumentWithId[F]): Txn[Unit]
  def fetchMetadata(uuid: UUID): Txn[Option[Metadata]]
  def fetchContent(uuid: UUID): Txn[Option[Stream[F, Byte]]]
  def delete(uuid: UUID): Txn[Unit]
