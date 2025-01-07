package org.fiume.sketch.shared.common.config

import cats.effect.{Ref, Sync}
import cats.implicits.*
import org.fiume.sketch.shared.common.config.DynamicConfig.Key

object DynamicConfig:
  trait Key[V]

/*
 * An experiemental highly general algebra for fetching dynamic configurations.
 */
trait DynamicConfig[F[_]]:
  def getConfig[V](key: Key[V]): F[Option[V]]

/*
 * A thread-safe concurrent in-memory version of `DynamicConfig`.
 */
object InMemoryDynamicConfig:
  def make[F[_]: Sync, Txn[_]: Sync](state: Map[Key[?], Any]): F[DynamicConfig[Txn]] =
    Ref.in[F, Txn, Map[Key[?], Any]](state).map { storage =>
      new DynamicConfig[Txn]():
        // There's a limitation on this implementation with the assumption that the caller knows
        // the type of value `V` of a given key `K`. A `ClassCastExpetion will be thrown at runtime in case of a mistake.
        override def getConfig[V](key: Key[V]): Txn[Option[V]] =
          storage.get.map(_.get(key).asInstanceOf[Option[V]])
    }
