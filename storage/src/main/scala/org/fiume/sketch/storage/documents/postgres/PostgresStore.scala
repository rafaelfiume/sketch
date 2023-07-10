package org.fiume.sketch.storage.documents.postgres

import cats.data.{NonEmptyList, OptionT}
import cats.effect.{Async, Clock, Resource}
import cats.implicits.*
import cats.~>
import doobie.*
import doobie.implicits.*
import fs2.Stream
import org.fiume.sketch.shared.app.algebras.HealthCheck
import org.fiume.sketch.shared.app.algebras.HealthCheck.ServiceHealth
import org.fiume.sketch.shared.app.algebras.HealthCheck.ServiceHealth.Infra
import org.fiume.sketch.shared.domain.documents.{Document, Metadata}
import org.fiume.sketch.storage.documents.algebras.DocumentsStore
import org.fiume.sketch.storage.documents.postgres.DoobieMappings
import org.fiume.sketch.storage.documents.postgres.DoobieMappings.given
import org.fiume.sketch.storage.postgres.{AbstractPostgresStore, Store}

import java.time.ZonedDateTime

object PostgresStore:
  def make[F[_]: Async](tx: Transactor[F]): Resource[F, PostgresStore[F]] =
    WeakAsync.liftK[F, ConnectionIO].map(l => new PostgresStore[F](l, tx))

private class PostgresStore[F[_]: Async] private (l: F ~> ConnectionIO, tx: Transactor[F])
    extends AbstractPostgresStore[F](l, tx)
    with DocumentsStore[F, ConnectionIO]
    with HealthCheck[F]:

  override def store(doc: Document[F]): ConnectionIO[Unit] =
    for
      // it's a shame current implementation ends up loading all the bytes in memory here
      // maybe one day that will change?
      array <- lift { doc.bytes.compile.toVector.map(_.toArray) }
      _ <- Statements.insertDocument(doc.metadata, array).run.void
    yield ()

  override def fetchMetadata(name: Metadata.Name): ConnectionIO[Option[Metadata]] =
    Statements.selectDocumentMetadata(name).option

  override def fetchBytes(name: Metadata.Name): ConnectionIO[Option[Stream[F, Byte]]] =
    // not the greatest implementation, since it will require bytes to be fully read from the db before the stream can start emiting bytes
    // this can be better optimised later (perhaps by storing/reading documents using a file sytem? or large objects?)
    // API is the most important part here.
    OptionT { Statements.selectDocumentBytes(name).option }
      .map(Stream.emits)
      .value

  override def delete(name: Metadata.Name): ConnectionIO[Unit] =
    Statements.delete(name).run.void

  override def check: F[ServiceHealth] =
    Statements.healthCheck
      .transact(tx)
      .as(ServiceHealth.healthy(Infra.Database))
      .recover(_ => ServiceHealth.faulty(Infra.Database))

private object Statements:
  val healthCheck: ConnectionIO[Int] = sql"select 42".query[Int].unique

  def insertDocument[F[_]](metadata: Metadata, bytes: Array[Byte]): Update0 =
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

  def selectDocumentMetadata(name: Metadata.Name): Query0[Metadata] =
    sql"""
         |SELECT
         |  d.name,
         |  d.description
         |FROM documents d
         |WHERE d.name = ${name}
    """.stripMargin.query[Metadata]

  def selectDocumentBytes(name: Metadata.Name): Query0[Array[Byte]] =
    sql"""
         |SELECT
         |  d.bytes
         |FROM documents d
         |WHERE d.name = ${name}
    """.stripMargin.query[Array[Byte]]

  def delete(name: Metadata.Name): Update0 =
    sql"""
         |DELETE
         |FROM documents d
         |WHERE d.name = ${name}
    """.stripMargin.update
