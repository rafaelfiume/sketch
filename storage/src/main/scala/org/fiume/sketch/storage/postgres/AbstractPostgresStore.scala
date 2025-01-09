package org.fiume.sketch.storage.postgres

import cats.effect.Sync
import cats.~>
import doobie.ConnectionIO
import doobie.implicits.*
import doobie.util.transactor.Transactor
import org.fiume.sketch.shared.common.app.Store

abstract class AbstractPostgresStore[F[_]: Sync] protected (l: F ~> ConnectionIO, tx: Transactor[F])
    extends Store[F, ConnectionIO]:

  override val lift: [A] => F[A] => ConnectionIO[A] = [A] => (fa: F[A]) => l(fa)

  override val commit: [A] => ConnectionIO[A] => F[A] = [A] => (txn: ConnectionIO[A]) => txn.transact(tx)

  override val commitStream: [A] => fs2.Stream[ConnectionIO, A] => fs2.Stream[F, A] = [A] =>
    (stream: fs2.Stream[ConnectionIO, A]) => stream.transact(tx)
