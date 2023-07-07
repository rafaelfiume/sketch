package org.fiume.sketch.storage.postgres

import cats.effect.Async
import cats.~>
import doobie.ConnectionIO
import doobie.implicits.*
import doobie.util.transactor.Transactor
import org.fiume.sketch.storage.algebras.Store

abstract class AbstractPostgresStore[F[_]: Async] protected (l: F ~> ConnectionIO, tx: Transactor[F])
    extends Store[F, ConnectionIO]:

  override val lift: [A] => F[A] => ConnectionIO[A] = [A] => (fa: F[A]) => l(fa)

  override val commit: [A] => ConnectionIO[A] => F[A] = [A] => (txn: ConnectionIO[A]) => txn.transact(tx)
