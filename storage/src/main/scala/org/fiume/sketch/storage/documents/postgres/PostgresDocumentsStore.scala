package org.fiume.sketch.storage.documents.postgres

import cats.data.NonEmptyList
import cats.effect.{Async, Resource}
import cats.implicits.*
import cats.~>
import doobie.*
import doobie.free.connection.ConnectionIO
import doobie.implicits.*
import org.fiume.sketch.shared.auth.UserId
import org.fiume.sketch.shared.domain.documents.{DocumentId, DocumentWithId, DocumentWithStream}
import org.fiume.sketch.shared.domain.documents.Document.Metadata
import org.fiume.sketch.shared.domain.documents.algebras.DocumentsStore
import org.fiume.sketch.storage.auth.postgres.DatabaseCodecs.given
import org.fiume.sketch.storage.documents.postgres.DatabaseCodecs.given
import org.fiume.sketch.storage.postgres.AbstractPostgresStore

object PostgresDocumentsStore:
  def make[F[_]: Async](tx: Transactor[F]): Resource[F, DocumentsStore[F, ConnectionIO]] =
    WeakAsync.liftK[F, ConnectionIO].map(lift => new PostgresDocumentsStore[F](lift, tx))

private class PostgresDocumentsStore[F[_]: Async] private (lift: F ~> ConnectionIO, tx: Transactor[F])
    extends AbstractPostgresStore[F](lift, tx)
    with DocumentsStore[F, ConnectionIO]:

  override def store(document: DocumentWithStream[F]): ConnectionIO[DocumentId] =
    for
      // Avoid reading all bytes into memory by using a large object?
      // https://typelevel.org/doobie/docs/15-Extensions-PostgreSQL.html
      bytes <- lift { Async[F].cede *> document.stream.compile.to(Array) <* Async[F].cede }
      uuid <- Statements
        .insertDocument(document.metadata, bytes)
        // Move this to Statements?
        .withUniqueGeneratedKeys[DocumentId]("uuid")
    yield uuid

  override def fetchDocument(uuid: DocumentId): ConnectionIO[Option[DocumentWithId]] =
    Statements.selectDocumentById(uuid).option

  override def documentStream(uuid: DocumentId): fs2.Stream[ConnectionIO, Byte] =
    Statements.selectDocumentBytesById(uuid).stream.flatMap(identity)

  private val documentsChunkSize = 50 // could be configurable
  override def fetchDocuments(uuids: fs2.Stream[ConnectionIO, DocumentId]): fs2.Stream[ConnectionIO, DocumentWithId] =
    uuids.chunkN(documentsChunkSize).flatMap { chunk =>
      chunk.toList.toNel match
        case None        => fs2.Stream.empty
        case Some(uuids) => Statements.selectDocumentsByIds(uuids).stream
    }

  def fetchDocumentsByOwnerId(userId: UserId): fs2.Stream[ConnectionIO, DocumentWithId] =
    Statements.selectDocumentsByOwnerId(userId).stream

  override def delete(uuid: DocumentId): ConnectionIO[Option[DocumentId]] =
    Statements.delete(uuid).option

private object Statements:
  def insertDocument(metadata: Metadata, bytes: Array[Byte]): Update0 =
    sql"""
         |INSERT INTO domain.documents(
         |  name,
         |  description,
         |  bytes,
         |  user_id
         |)
         |VALUES (
         |  ${metadata.name},
         |  ${metadata.description},
         |  $bytes,
         |  ${metadata.ownerId}
         |)
    """.stripMargin.update

  def selectDocumentById(uuid: DocumentId): Query0[DocumentWithId] =
    sql"""
         |SELECT
         |  d.uuid,
         |  d.name,
         |  d.description,
         |  d.user_id
         |FROM domain.documents d
         |WHERE d.uuid = $uuid
    """.stripMargin.query[DocumentWithId]

  def selectDocumentBytesById(uuid: DocumentId): Query0[fs2.Stream[ConnectionIO, Byte]] =
    sql"""
         |SELECT
         |  d.bytes
         |FROM domain.documents d
         |WHERE d.uuid = $uuid
    """.stripMargin.query[fs2.Stream[ConnectionIO, Byte]]

  def selectDocumentsByIds(uuids: NonEmptyList[DocumentId]): Query0[DocumentWithId] =
    val in = Fragments.in(fr"uuid", uuids)
    val query = fr"""
       |SELECT
       |  d.uuid,
       |  d.name,
       |  d.description,
       |  d.user_id
       |FROM domain.documents d
       |WHERE
    """.stripMargin ++ in
    query.query[DocumentWithId]

  def selectDocumentsByOwnerId(ownerId: UserId): Query0[DocumentWithId] =
    sql"""
         |SELECT
         |  d.uuid,
         |  d.name,
         |  d.description,
         |  d.user_id
         |FROM domain.documents d
         |WHERE d.user_id = $ownerId
    """.stripMargin.query[DocumentWithId]

  def delete(uuid: DocumentId): Query0[DocumentId] =
    sql"""
         |DELETE
         |FROM domain.documents d
         |WHERE d.uuid = $uuid
         |RETURNING d.uuid
    """.stripMargin.query[DocumentId]
