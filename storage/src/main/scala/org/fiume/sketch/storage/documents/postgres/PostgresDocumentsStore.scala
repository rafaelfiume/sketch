package org.fiume.sketch.storage.documents.postgres

import cats.data.{NonEmptyList, OptionT}
import cats.effect.{Async, Clock, Resource}
import cats.implicits.*
import cats.~>
import doobie.*
import doobie.free.connection.ConnectionIO
import doobie.implicits.*
import doobie.postgres.implicits.*
import fs2.Stream
import org.fiume.sketch.storage.documents.Model.{Document, Metadata}
import org.fiume.sketch.storage.documents.algebras.DocumentsStore
import org.fiume.sketch.storage.documents.postgres.DoobieMappings
import org.fiume.sketch.storage.documents.postgres.DoobieMappings.given
import org.fiume.sketch.storage.postgres.{AbstractPostgresStore, Store}

import java.time.ZonedDateTime
import java.util.UUID

object PostgresDocumentsStore:
  def make[F[_]: Async](tx: Transactor[F]): Resource[F, PostgresDocumentsStore[F]] =
    WeakAsync.liftK[F, ConnectionIO].map(l => new PostgresDocumentsStore[F](l, tx))

private class PostgresDocumentsStore[F[_]: Async] private (l: F ~> ConnectionIO, tx: Transactor[F])
    extends AbstractPostgresStore[F](l, tx)
    with DocumentsStore[F, ConnectionIO]:

  override def store(metadata: Metadata, content: Stream[F, Byte]): ConnectionIO[UUID] =
    for
      // it's a shame current implementation ends up loading all the bytes in memory here
      // maybe one day that will change?
      content <- lift { content.compile.toVector.map(_.toArray) }
      uuid <- Statements
        .insertDocument(metadata, content)
        .withUniqueGeneratedKeys[UUID](
          "uuid"
        )
    yield uuid

  override def update(uuid: UUID, metadata: Metadata, content: Stream[F, Byte]): ConnectionIO[Unit] =
    for
      content <- lift { content.compile.toVector.map(_.toArray) }
      _ <- Statements.update(uuid, metadata, content).run.void
    yield ()

  override def fetchMetadata(uuid: UUID): ConnectionIO[Option[Metadata]] =
    Statements.selectDocumentMetadata(uuid).option

  override def fetchBytes(uuid: UUID): ConnectionIO[Option[Stream[F, Byte]]] =
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
         |INSERT INTO documents(
         |  name,
         |  description,
         |  bytes
         |)
         |VALUES (
         |  ${metadata.name},
         |  ${metadata.description},
         |  ${content}
         |)
    """.stripMargin.update

  def update(uuid: UUID, metadata: Metadata, content: Array[Byte]): Update0 =
    sql"""
         |UPDATE documents
         |SET
         |  name = ${metadata.name},
         |  description = ${metadata.description},
         |  bytes = ${content}
         |WHERE uuid = ${uuid}
    """.stripMargin.update

  def selectDocumentMetadata(id: UUID): Query0[Metadata] =
    sql"""
         |SELECT
         |  d.name,
         |  d.description
         |FROM documents d
         |WHERE d.uuid = $id
    """.stripMargin.query[Metadata]

  def selectDocumentBytes(uuid: UUID): Query0[Array[Byte]] =
    sql"""
         |SELECT
         |  d.bytes
         |FROM documents d
         |WHERE d.uuid = $uuid
    """.stripMargin.query[Array[Byte]]

  def delete(uuid: UUID): Update0 =
    sql"""
         |DELETE
         |FROM documents d
         |WHERE d.uuid = ${uuid}
    """.stripMargin.update
