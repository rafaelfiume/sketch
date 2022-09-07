package org.fiume.sketch.datastore.postgres

import cats.effect.{Async, Clock, Resource}
import cats.implicits.*
import cats.~>
import doobie.*
import doobie.implicits.*
import fs2.Stream
import org.fiume.sketch.algebras.*
import org.fiume.sketch.datastore.algebras.{DocumentStore, Store}
import org.fiume.sketch.datastore.postgres.Statements.*
import org.fiume.sketch.domain.Document
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object PostgresStore:
  def make[F[_]: Async](clock: Clock[F], tx: Transactor[F]): Resource[F, PostgresStore[F]] =
    WeakAsync.liftK[F, ConnectionIO].map(l => new PostgresStore[F](clock, l, tx))

private class PostgresStore[F[_]: Async] private (clock: Clock[F], l: F ~> ConnectionIO, tx: Transactor[F])
    extends Store[F, ConnectionIO]
    with DocumentStore[F, ConnectionIO]
    with HealthCheck[F]:

  private val logger = Slf4jLogger.getLogger[F]

  override val lift: [A] => F[A] => ConnectionIO[A] = [A] => (fa: F[A]) => l(fa)

  override val commit: [A] => ConnectionIO[A] => F[A] = [A] => (txn: ConnectionIO[A]) => txn.transact(tx)

  override def store(doc: Document): ConnectionIO[Unit] =
    Statements.insertDocument(doc).run.void

  override def fetchMetadata(name: Document.Metadata.Name): ConnectionIO[Document.Metadata] =
    Statements.selectDocumentMetadata(name).unique

  override def fetchBytes(name: Document.Metadata.Name): ConnectionIO[Stream[F, Byte]] =
    // not the greatest implementation, since it will require bytes to be fully read from the db before the stream can start emiting bytes
    // this can be better optimised later (perhaps by storing/reading documents using a file sytem? or large objects?)
    // API is the most important part here.
    Statements.selectDocumentBytes(name).unique.map(Stream.emits)

  override def healthCheck: F[Unit] = Statements.healthCheck.transact(tx).void
