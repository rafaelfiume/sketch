package org.fiume.sketch.storage.config.postgres

import cats.effect.IO
import cats.implicits.*
import doobie.ConnectionIO
import doobie.implicits.*
import doobie.util.Write
import io.circe.{Decoder, Encoder}
import io.circe.syntax.*
import munit.CatsEffectSuite
import org.fiume.sketch.shared.common.config.{DynamicConfig, Namespace}
import org.fiume.sketch.shared.common.typeclasses.AsString
import org.fiume.sketch.shared.testkit.syntax.OptionSyntax.*
import org.fiume.sketch.storage.config.postgres.DatabaseCodecs.given
import org.fiume.sketch.storage.config.postgres.PostgresDynamicConfigStoreSpecContext.*
import org.fiume.sketch.storage.config.postgres.PostgresDynamicConfigStoreSpecContext.given
import org.fiume.sketch.storage.postgres.PostgresTransactionManager
import org.fiume.sketch.storage.testkit.DockerPostgresSuite

class PostgresDynamicConfigStoreSpec extends CatsEffectSuite with PostgresDynamicConfigStoreSpecContext:

  test("retrieves dynamic configuration from a Postgres provider"):
    will(cleanStorage) {
      val namespace = Namespace("it-test")
      val value = Set(SampleValue("change-me-at-runtime"))
      (
        PostgresDynamicConfigStore.makeForNamespace[IO](namespace),
        PostgresTransactionManager.make[IO](transactor())
      ).tupled.use { case (provider, tx) =>
        for
          _ <- tx.commit { givenConfig(namespace, SampleKey, value) }

          result <- tx.commit { provider.getConfig(SampleKey).map(_.someOrFail) }
//
        yield assertEquals(result, value)
      }
    }

  // TODO Check constraints

object PostgresDynamicConfigStoreSpecContext:
  case object SampleKey extends DynamicConfig.Key[Set[SampleValue]]
  case class SampleValue(value: String) extends AnyVal

  given Encoder[SampleValue] = Encoder.encodeString.contramap(_.value)
  given Decoder[SampleValue] = Decoder.decodeString.map(SampleValue(_))

  given AsString[SampleKey.type]:
    extension (key: SampleKey.type) override def asString() = "sample.key"

trait PostgresDynamicConfigStoreSpecContext extends DockerPostgresSuite:
  def cleanStorage: ConnectionIO[Unit] = sql"TRUNCATE TABLE system.dynamic_configs".update.run.void

  def givenConfig[K <: DynamicConfig.Key[V], V](namespace: Namespace, key: K, value: V)(using
    AsString[K],
    Encoder[V]
  ): ConnectionIO[Unit] =
    sql"""
         |INSERT INTO system.dynamic_configs (
         |  namespace,
         |  key,
         |  value
         |) VALUES (
         |  $namespace,
         |  ${key.asString()},
         |  ${value.asJson}
         |)
    """.stripMargin.update.run.void
