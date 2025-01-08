package org.fiume.sketch.shared.common.config

import cats.effect.{Ref, Sync}
import cats.implicits.*
import io.circe.{Decoder, Encoder, Json}
import io.circe.parser.decode
import io.circe.syntax.*
import org.fiume.sketch.shared.common.config.DynamicConfig.Key
import org.fiume.sketch.shared.common.typeclasses.AsString

object DynamicConfig:
  trait Key[V]

/*
 * An experiemental highly general algebra for fetching dynamic configurations.
 */
trait DynamicConfig[F[_]]:
  def getConfig[K <: Key[V], V](key: K)(using AsString[K], Decoder[V]): F[Option[V]]

/*
 * A thread-safe concurrent in-memory version of `DynamicConfig`.
 */
object InMemoryDynamicConfig:
  def makeWithKvPair[F[_]: Sync, Txn[_]: Sync, K <: Key[V], V](key: K, value: V)(using AsString[K], Encoder[V]) =
    make[F, Txn](Map(key.asString() -> value.asJson))

  def make[F[_]: Sync, Txn[_]: Sync](state: Map[String, Json]): F[DynamicConfig[Txn]] =
    Ref.in[F, Txn, Map[String, Json]](state).map { storage =>
      new DynamicConfig[Txn]():
        override def getConfig[K <: Key[V], V](key: K)(using AsString[K], Decoder[V]): Txn[Option[V]] =
          storage.get.map {
            // TODO Refine error handling
            // Add note on why sticking with `Option` and not returning `Either`
            _.get(key.asString()).map(_.noSpaces).map(decode[V](_).toOption.get)
          }
    }
