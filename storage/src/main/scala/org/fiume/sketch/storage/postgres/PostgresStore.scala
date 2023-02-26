package org.fiume.sketch.storage.postgres

import cats.data.OptionT
import cats.effect.{Async, Clock, Resource}
import cats.implicits.*
import cats.~>
import doobie.*
import doobie.implicits.*
import fs2.Stream
import org.fiume.sketch.algebras.*
import org.fiume.sketch.domain.documents.{Document, Metadata}
import org.fiume.sketch.storage.algebras.{DocumentsStore, Store}
import org.fiume.sketch.storage.postgres.Statements.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object PostgresStore:
  def make[F[_]: Async](tx: Transactor[F]): Resource[F, PostgresStore[F]] =
    WeakAsync.liftK[F, ConnectionIO].map(l => new PostgresStore[F](l, tx))

private class PostgresStore[F[_]: Async] private (l: F ~> ConnectionIO, tx: Transactor[F])
    extends Store[F, ConnectionIO]
    with DocumentsStore[F, ConnectionIO]
    with HealthCheck[F]:

  private val logger = Slf4jLogger.getLogger[F]

  override val lift: [A] => F[A] => ConnectionIO[A] = [A] => (fa: F[A]) => l(fa)

  override val commit: [A] => ConnectionIO[A] => F[A] = [A] => (txn: ConnectionIO[A]) => txn.transact(tx)

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

  override def healthCheck: F[Boolean] = Statements.healthCheck.transact(tx).as(true).recover(_ => false)
