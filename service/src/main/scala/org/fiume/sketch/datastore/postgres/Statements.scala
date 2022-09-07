package org.fiume.sketch.datastore.postgres

import cats.data.NonEmptyList
import cats.implicits.*
import doobie.*
import doobie.implicits.*
import org.fiume.sketch.domain.Document
import org.fiume.sketch.datastore.postgres.DoobieMappings.given

import java.time.ZonedDateTime

private[postgres] object Statements:
  val healthCheck: ConnectionIO[Int] = sql"select 42".query[Int].unique

  def insertDocument(document: Document): Update0 =
    import document.*
    sql"""
      INSERT INTO documents(
        name,
        description,
        bytes
      )
      VALUES (
        ${metadata.name},
        ${metadata.description},
        ${bytes}
      )
    """.stripMargin.update

  def selectDocumentMetadata(name: Document.Metadata.Name): Query0[Document.Metadata] =
    sql"""
      SELECT
        d.name,
        d.description
      FROM documents d
      WHERE d.name = ${name}
    """.stripMargin.query[Document.Metadata]

  def selectDocumentBytes(name: Document.Metadata.Name): Query0[Array[Byte]] =
    sql"""
      SELECT
        d.bytes
      FROM documents d
      WHERE d.name = ${name}
    """.stripMargin.query[Array[Byte]]
