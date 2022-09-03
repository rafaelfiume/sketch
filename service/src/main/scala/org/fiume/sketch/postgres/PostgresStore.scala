package org.fiume.sketch.postgres

import cats.effect.{Async, Clock, Resource}
import cats.implicits.*
import cats.~>
import doobie.*
import doobie.implicits.*
import org.fiume.sketch.algebras.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object PostgresStore:
  def make[F[_]: Async](clock: Clock[F], tx: Transactor[F]): Resource[F, PostgresStore[F]] =
    WeakAsync.liftK[F, ConnectionIO].map(l => new PostgresStore[F](clock, l, tx))

class PostgresStore[F[_]: Async] private (clock: Clock[F], l: F ~> ConnectionIO, tx: Transactor[F])
    extends Store[F, ConnectionIO]
    with HealthCheck[F]:

  private val logger = Slf4jLogger.getLogger[F]

  override val lift: [A] => F[A] => ConnectionIO[A] = [A] => (fa: F[A]) => l(fa)

  override val commit: [A] => ConnectionIO[A] => F[A] = [A] => (txn: ConnectionIO[A]) => txn.transact(tx)

  override def healthCheck: F[Unit] = Statements.healthCheck.transact(tx).void
