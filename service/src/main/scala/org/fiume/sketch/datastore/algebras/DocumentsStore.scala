package org.fiume.sketch.datastore.algebras

import fs2.Stream
import org.fiume.sketch.domain.documents.{Document, Metadata}

trait DocumentsStore[F[_], Txn[_]] extends Store[F, Txn]:

  def store(doc: Document[F]): Txn[Unit]
  def fetchMetadata(name: Metadata.Name): Txn[Option[Metadata]]
  def fetchBytes(name: Metadata.Name): Txn[Option[Stream[F, Byte]]]
  def delete(name: Metadata.Name): Txn[Unit]
