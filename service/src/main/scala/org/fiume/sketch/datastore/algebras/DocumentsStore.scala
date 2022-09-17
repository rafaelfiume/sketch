package org.fiume.sketch.datastore.algebras

import fs2.Stream
import org.fiume.sketch.domain.Document

trait DocumentsStore[F[_], Txn[_]] extends Store[F, Txn]:

  def store(doc: Document[F]): Txn[Unit]
  def fetchMetadata(name: Document.Metadata.Name): Txn[Option[Document.Metadata]]
  def fetchBytes(name: Document.Metadata.Name): Txn[Option[Stream[F, Byte]]]
  def delete(name: Document.Metadata.Name): Txn[Unit]
