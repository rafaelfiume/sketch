package org.fiume.sketch.storage.config.postgres

import cats.effect.{Async, Resource}
import doobie.free.connection.ConnectionIO
import doobie.implicits.*
import doobie.util.query.Query0
import io.circe.{Decoder, Json}
import io.circe.parser.decode
import org.fiume.sketch.shared.common.config.{DynamicConfig, Namespace}
import org.fiume.sketch.shared.common.config.DynamicConfig.Key
import org.fiume.sketch.shared.common.typeclasses.AsString
import org.fiume.sketch.storage.config.postgres.DatabaseCodecs.given

object PostgresDynamicConfigStore:
  def makeForNamespace[F[_]: Async](namespace: Namespace): Resource[F, PostgresDynamicConfigStore] =
    Resource.pure(new PostgresDynamicConfigStore(namespace))

private class PostgresDynamicConfigStore private (namespace: Namespace) extends DynamicConfig[ConnectionIO]:

  override def getConfig[K <: Key[V], V](key: K)(using AsString[K], Decoder[V]): ConnectionIO[Option[V]] =
    Statements.selectConfig(namespace, key).option

private object Statements:
  def selectConfig[K <: Key[V], V](namespace: Namespace, key: K)(using AsString[K], Decoder[V]): Query0[V] =
    sql"""
         |SELECT
         |  value
         |FROM system.dynamic_configs
         |WHERE namespace = ${namespace} AND key = ${key.asString()}
    """.stripMargin
      .query[Json]
      .map { json =>
        decode[V](json.noSpaces).toOption.getOrElse(throw new IllegalStateException(s"unparsable json: $json"))
      }
