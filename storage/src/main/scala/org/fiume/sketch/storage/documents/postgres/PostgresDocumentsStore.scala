package org.fiume.sketch.storage.documents.postgres

import cats.data.OptionT
import cats.effect.{Async, Resource}
import cats.implicits.*
import cats.~>
import doobie.*
import doobie.free.connection.ConnectionIO
import doobie.implicits.*
import doobie.postgres.implicits.*
import fs2.Stream
import org.fiume.sketch.storage.documents.{Document, DocumentWithId}
import org.fiume.sketch.storage.documents.Document.Metadata
import org.fiume.sketch.storage.documents.algebras.DocumentsStore
import org.fiume.sketch.storage.documents.postgres.DoobieMappings.given
import org.fiume.sketch.storage.postgres.AbstractPostgresStore

import java.util.UUID

object PostgresDocumentsStore:
  def make[F[_]: Async](tx: Transactor[F]): Resource[F, PostgresDocumentsStore[F]] =
    WeakAsync.liftK[F, ConnectionIO].map(l => new PostgresDocumentsStore[F](l, tx))

private class PostgresDocumentsStore[F[_]: Async] private (l: F ~> ConnectionIO, tx: Transactor[F])
    extends AbstractPostgresStore[F](l, tx)
    with DocumentsStore[F, ConnectionIO]:

  override def store(document: Document[F]): ConnectionIO[UUID] =
    for
      // Avoid reading all bytes into memory by using a large object?
      // https://tpolecat.github.io/doobie-cats-0.4.2/15-Extensions-PostgreSQL.html
      // https://github.com/tpolecat/doobie/blob/32838f90044f5c3acac6b9f4ae7a2be10b5f1bb0/modules/postgres/src/main/scala/doobie/postgres/hi/largeobjectmanager.scala#L34
      // https://github.com/tpolecat/doobie/blob/32838f90044f5c3acac6b9f4ae7a2be10b5f1bb0/modules/example/src/main/scala/example/PostgresLargeObject.scala#L18
      bytes <- lift { Async[F].cede *> document.content.compile.toVector.map(_.toArray) <* Async[F].cede }
      uuid <- Statements
        .insertDocument(document.metadata, bytes)
        .withUniqueGeneratedKeys[UUID](
          "uuid"
        )
    yield uuid

  override def update(document: DocumentWithId[F]): ConnectionIO[Unit] =
    for
      bytes <- lift { Async[F].cede *> document.content.compile.toVector.map(_.toArray) <* Async[F].cede }
      _ <- Statements.update(document.uuid, document.metadata, bytes).run.void
    yield ()

  override def fetchMetadata(uuid: UUID): ConnectionIO[Option[Metadata]] =
    Statements.selectDocumentMetadata(uuid).option

  override def fetchContent(uuid: UUID): ConnectionIO[Option[Stream[F, Byte]]] =
    // not the greatest implementation, since it will require bytes to be fully read from the db before the stream can start emiting bytes
    // this can be better optimised later (perhaps by storing/reading documents using a file sytem? or large objects?)
    // API is the most important part here.
    OptionT { Statements.selectDocumentBytes(uuid).option }
      .map(Stream.emits)
      .value

  override def delete(uuid: UUID): ConnectionIO[Unit] =
    Statements.delete(uuid).run.void

private object Statements:
  def insertDocument[F[_]](metadata: Metadata, content: Array[Byte]): Update0 =
    sql"""
         |INSERT INTO domain.documents(
         |  name,
         |  description,
         |  bytes
         |)
         |VALUES (
         |  ${metadata.name},
         |  ${metadata.description},
         |  $content
         |)
    """.stripMargin.update

  def update(uuid: UUID, metadata: Metadata, content: Array[Byte]): Update0 =
    sql"""
         |UPDATE domain.documents
         |SET
         |  name = ${metadata.name},
         |  description = ${metadata.description},
         |  bytes = $content
         |WHERE uuid = $uuid
    """.stripMargin.update

  def selectDocumentMetadata(uuid: UUID): Query0[Metadata] =
    sql"""
         |SELECT
         |  d.name,
         |  d.description
         |FROM domain.documents d
         |WHERE d.uuid = $uuid
    """.stripMargin.query[Metadata]

  def selectDocumentBytes(uuid: UUID): Query0[Array[Byte]] =
    sql"""
         |SELECT
         |  d.bytes
         |FROM domain.documents d
         |WHERE d.uuid = $uuid
    """.stripMargin.query[Array[Byte]]

  def delete(uuid: UUID): Update0 =
    sql"""
         |DELETE
         |FROM domain.documents d
         |WHERE d.uuid = $uuid
    """.stripMargin.update
