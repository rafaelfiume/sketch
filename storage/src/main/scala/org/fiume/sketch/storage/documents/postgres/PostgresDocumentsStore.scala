package org.fiume.sketch.storage.documents.postgres

import cats.data.OptionT
import cats.effect.{Async, Resource}
import cats.implicits.*
import cats.~>
import doobie.*
import doobie.free.connection.ConnectionIO
import doobie.implicits.*
import fs2.Stream
import org.fiume.sketch.shared.auth0.UserId
import org.fiume.sketch.storage.auth0.postgres.DoobieMappings.given
import org.fiume.sketch.storage.documents.{Document, DocumentId, DocumentWithId, DocumentWithStream}
import org.fiume.sketch.storage.documents.Document.Metadata
import org.fiume.sketch.storage.documents.algebras.DocumentsStore
import org.fiume.sketch.storage.documents.postgres.DoobieMappings.given
import org.fiume.sketch.storage.postgres.AbstractPostgresStore

object PostgresDocumentsStore:
  def make[F[_]: Async](tx: Transactor[F]): Resource[F, PostgresDocumentsStore[F]] =
    WeakAsync.liftK[F, ConnectionIO].map(l => new PostgresDocumentsStore[F](l, tx))

private class PostgresDocumentsStore[F[_]: Async] private (l: F ~> ConnectionIO, tx: Transactor[F])
    extends AbstractPostgresStore[F](l, tx)
    with DocumentsStore[F, ConnectionIO]:

  override def store(document: DocumentWithStream[F]): ConnectionIO[DocumentId] =
    for
      // Avoid reading all bytes into memory by using a large object?
      // https://tpolecat.github.io/doobie-cats-0.4.2/15-Extensions-PostgreSQL.html
      // https://github.com/tpolecat/doobie/blob/32838f90044f5c3acac6b9f4ae7a2be10b5f1bb0/modules/postgres/src/main/scala/doobie/postgres/hi/largeobjectmanager.scala#L34
      // https://github.com/tpolecat/doobie/blob/32838f90044f5c3acac6b9f4ae7a2be10b5f1bb0/modules/example/src/main/scala/example/PostgresLargeObject.scala#L18
      bytes <- lift { Async[F].cede *> document.stream.compile.toVector.map(_.toArray) <* Async[F].cede }
      uuid <- Statements
        .insertDocument(document.metadata, bytes)
        .withUniqueGeneratedKeys[DocumentId](
          "uuid"
        )
    yield uuid

  override def fetchDocument(uuid: DocumentId): ConnectionIO[Option[DocumentWithId]] =
    Statements.selectDocument(uuid).option

  override def documentStream(uuid: DocumentId): ConnectionIO[Option[Stream[F, Byte]]] =
    // not the greatest implementation, since it will require bytes to be fully read from the db before the stream can start emiting bytes
    // this can be better optimised later (perhaps by storing/reading documents using a file sytem? or large objects?)
    // API is the most important part here.
    OptionT { Statements.selectDocumentBytes(uuid).option }
      .map(Stream.emits)
      .value

  def fetchByAuthor(by: UserId): fs2.Stream[F, DocumentWithId] =
    Statements.selectByAuthor(by).transact(tx)

  def fetchByOwner(by: UserId): fs2.Stream[F, DocumentWithId] =
    Statements.selectByOwner(by).transact(tx)

  override def delete(uuid: DocumentId): ConnectionIO[Unit] =
    Statements.delete(uuid).run.void

private object Statements:
  def insertDocument(metadata: Metadata, bytes: Array[Byte]): Update0 =
    sql"""
         |INSERT INTO domain.documents(
         |  name,
         |  description,
         |  created_by,
         |  owned_by,
         |  bytes
         |)
         |VALUES (
         |  ${metadata.name},
         |  ${metadata.description},
         |  ${metadata.createdBy},
         |  ${metadata.ownedBy},
         |  $bytes
         |)
    """.stripMargin.update

  def selectDocument(uuid: DocumentId): Query0[DocumentWithId] =
    sql"""
         |SELECT
         |  d.uuid,
         |  d.name,
         |  d.description,
         |  d.created_by,
         |  d.owned_by
         |FROM domain.documents d
         |WHERE d.uuid = $uuid
    """.stripMargin.query[DocumentWithId]

  def selectDocumentBytes(uuid: DocumentId): Query0[Array[Byte]] =
    sql"""
         |SELECT
         |  d.bytes
         |FROM domain.documents d
         |WHERE d.uuid = $uuid
    """.stripMargin.query[Array[Byte]]

  def selectByAuthor(createdBy: UserId): fs2.Stream[ConnectionIO, DocumentWithId] =
    sql"""
         |SELECT
         |  d.uuid,
         |  d.name,
         |  d.description,
         |  d.created_by,
         |  d.owned_by
         |FROM domain.documents d
         |WHERE d.created_by = $createdBy
    """.stripMargin.query[DocumentWithId].stream

  def selectByOwner(ownerId: UserId): fs2.Stream[ConnectionIO, DocumentWithId] =
    sql"""
         |SELECT
         |  d.uuid,
         |  d.name,
         |  d.description,
         |  d.created_by,
         |  d.owned_by
         |FROM domain.documents d
         |WHERE d.owned_by = $ownerId
    """.stripMargin.query[DocumentWithId].stream

  def delete(uuid: DocumentId): Update0 =
    sql"""
         |DELETE
         |FROM domain.documents d
         |WHERE d.uuid = $uuid
    """.stripMargin.update
