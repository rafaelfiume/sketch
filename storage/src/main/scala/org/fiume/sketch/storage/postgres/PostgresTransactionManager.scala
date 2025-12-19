package org.fiume.sketch.storage.postgres

import cats.effect.{Async, Concurrent, Resource}
import cats.~>
import doobie.{ConnectionIO, Transactor, WeakAsync}
import doobie.implicits.*
import doobie.util.transactor.Transactor
import org.fiume.sketch.shared.common.app.TransactionManager

object PostgresTransactionManager:
  def make[F[_]: Async](tx: Transactor[F]): Resource[F, PostgresTransactionManager[F]] =
    WeakAsync.liftK[F, ConnectionIO].map(lift => new PostgresTransactionManager(lift, tx))

class PostgresTransactionManager[F[_]: Concurrent] private (
  private val l: F ~> ConnectionIO,
  private val tx: Transactor[F]
) extends TransactionManager[F, ConnectionIO]:

  override val lift: [A] => F[A] => ConnectionIO[A] =
    [A] => (fa: F[A]) => l(fa)

  override val commit: [A] => ConnectionIO[A] => F[A] =
    [A] => (txn: ConnectionIO[A]) => txn.transact(tx)

  override val commitStream: [A] => fs2.Stream[ConnectionIO, A] => fs2.Stream[F, A] = [A] =>
    (stream: fs2.Stream[ConnectionIO, A]) => stream.transact(tx)
