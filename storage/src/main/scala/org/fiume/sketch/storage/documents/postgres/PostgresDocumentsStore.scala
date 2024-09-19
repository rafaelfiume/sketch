package org.fiume.sketch.storage.documents.postgres

import cats.data.NonEmptyList
import cats.effect.{Async, Resource}
import cats.implicits.*
import cats.~>
import doobie.*
import doobie.free.connection.ConnectionIO
import doobie.implicits.*
import org.fiume.sketch.shared.domain.documents.{Document, DocumentId, DocumentWithId, DocumentWithStream}
import org.fiume.sketch.shared.domain.documents.Document.Metadata
import org.fiume.sketch.shared.domain.documents.algebras.DocumentsStore
import org.fiume.sketch.storage.documents.postgres.DoobieMappings.given
import org.fiume.sketch.storage.postgres.AbstractPostgresStore

object PostgresDocumentsStore:
  def make[F[_]: Async](tx: Transactor[F]): Resource[F, PostgresDocumentsStore[F]] =
    WeakAsync.liftK[F, ConnectionIO].map(lift => new PostgresDocumentsStore[F](lift, tx))

private class PostgresDocumentsStore[F[_]: Async] private (lift: F ~> ConnectionIO, tx: Transactor[F])
    extends AbstractPostgresStore[F](lift, tx)
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
        // Move this to Statements?
        .withUniqueGeneratedKeys[DocumentId]("uuid")
    yield uuid

  override def fetchDocument(uuid: DocumentId): ConnectionIO[Option[DocumentWithId]] =
    Statements.selectDocument(uuid).option

  override def documentStream(uuid: DocumentId): fs2.Stream[ConnectionIO, Byte] =
    Statements.selectDocumentBytes(uuid)

  private val documentsChunkSize = 50
  override def fetchDocuments(uuids: fs2.Stream[ConnectionIO, DocumentId]): fs2.Stream[ConnectionIO, DocumentWithId] =
    uuids.chunkN(documentsChunkSize).flatMap { chunk =>
      chunk.toList.toNel match
        case None        => fs2.Stream.empty
        case Some(uuids) => Statements.selectByIds(uuids)
    }

  override def delete(uuid: DocumentId): ConnectionIO[Unit] =
    Statements.delete(uuid).run.void

private object Statements:
  def insertDocument(metadata: Metadata, bytes: Array[Byte]): Update0 =
    sql"""
         |INSERT INTO domain.documents(
         |  name,
         |  description,
         |  bytes
         |)
         |VALUES (
         |  ${metadata.name},
         |  ${metadata.description},
         |  $bytes
         |)
    """.stripMargin.update

  def selectDocument(uuid: DocumentId): Query0[DocumentWithId] =
    sql"""
         |SELECT
         |  d.uuid,
         |  d.name,
         |  d.description
         |FROM domain.documents d
         |WHERE d.uuid = $uuid
    """.stripMargin.query[DocumentWithId]

  def selectDocumentBytes(uuid: DocumentId): fs2.Stream[ConnectionIO, Byte] =
    // This seems to be loading all document bytes in memory?
    sql"""
         |SELECT
         |  d.bytes
         |FROM domain.documents d
         |WHERE d.uuid = $uuid
    """.stripMargin.query[Array[Byte]].stream.flatMap(fs2.Stream.emits)

  def selectByIds(uuids: NonEmptyList[DocumentId]): fs2.Stream[ConnectionIO, DocumentWithId] =
    val in = Fragments.in(fr"uuid", uuids)
    val query = fr"""
       |SELECT
       |  d.uuid,
       |  d.name,
       |  d.description
       |FROM domain.documents d
       |WHERE
    """.stripMargin ++ in
    query.query[DocumentWithId].stream

  def delete(uuid: DocumentId): Update0 =
    sql"""
         |DELETE
         |FROM domain.documents d
         |WHERE d.uuid = $uuid
    """.stripMargin.update
