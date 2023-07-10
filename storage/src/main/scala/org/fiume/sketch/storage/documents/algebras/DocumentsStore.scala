package org.fiume.sketch.storage.documents.algebras

import fs2.Stream
import org.fiume.sketch.shared.domain.documents.{Document, Metadata}
import org.fiume.sketch.storage.postgres.Store

trait DocumentsStore[F[_], Txn[_]] extends Store[F, Txn]:

  def store(doc: Document[F]): Txn[Unit]
  def fetchMetadata(name: Metadata.Name): Txn[Option[Metadata]]
  def fetchBytes(name: Metadata.Name): Txn[Option[Stream[F, Byte]]]
  def delete(name: Metadata.Name): Txn[Unit]
