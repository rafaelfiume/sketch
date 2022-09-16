package org.fiume.sketch.datastore.postgres

import cats.data.NonEmptyList
import cats.implicits.*
import doobie.*
import doobie.implicits.*
import org.fiume.sketch.datastore.postgres.DoobieMappings.given
import org.fiume.sketch.domain.Document

import java.time.ZonedDateTime

private[postgres] object Statements:
  val healthCheck: ConnectionIO[Int] = sql"select 42".query[Int].unique

  def insertDocument[F[_]](metadata: Document.Metadata, bytes: Array[Byte]): Update0 =
    sql"""
      |INSERT INTO documents(
      |  name,
      |  description,
      |  bytes
      |)
      |VALUES (
      |  ${metadata.name},
      |  ${metadata.description},
      |  ${bytes}
      |)
      |ON CONFLICT (name) DO
      |UPDATE SET
      |  name = EXCLUDED.name,
      |  description = EXCLUDED.description,
      |  updated_at_utc = EXCLUDED.updated_at_utc,
      |  bytes = EXCLUDED.bytes
    """.stripMargin.update

  def selectDocumentMetadata(name: Document.Metadata.Name): Query0[Document.Metadata] =
    sql"""
      |SELECT
      |  d.name,
      |  d.description
      |FROM documents d
      |WHERE d.name = ${name}
    """.stripMargin.query[Document.Metadata]

  def selectDocumentBytes(name: Document.Metadata.Name): Query0[Array[Byte]] =
    sql"""
      |SELECT
      |  d.bytes
      |FROM documents d
      |WHERE d.name = ${name}
    """.stripMargin.query[Array[Byte]]
