package org.fiume.sketch.app

import cats.effect.Async
import cats.implicits.*
import ciris.*
import org.fiume.sketch.auth0.KeyStringifier
import org.fiume.sketch.shared.app.algebras.Versions.Environment
import org.fiume.sketch.storage.DatabaseConfig

import java.security.interfaces.{ECPrivateKey, ECPublicKey}

case class ServiceConfig(
  env: Environment,
  keyPair: EcKeyPairConfig,
  db: DatabaseConfig
)

case class EcKeyPairConfig(privateKey: ECPrivateKey, publicKey: ECPublicKey)

object ServiceConfig:
  given ConfigDecoder[String, Environment] = ConfigDecoder[String].map(Environment.apply)
  given ConfigDecoder[String, ECPrivateKey] =
    ConfigDecoder[String].mapEither((_, key) => KeyStringifier.ecPrivateKeyFromPem(key).leftMap(ConfigError(_)))
  given ConfigDecoder[String, ECPublicKey] =
    ConfigDecoder[String].mapEither((_, key) => KeyStringifier.ecPublicKeyFromPem(key).leftMap(ConfigError(_)))

  def load[F[_]: Async]: F[ServiceConfig] =
    (for
      environment <- env("ENV").as[Environment]
      privateKey <- env("PRIVATE_KEY").as[ECPrivateKey].redacted
      publicKey <- env("PUBLIC_KEY").as[ECPublicKey].redacted
      databaseConfig <- DatabaseConfig.load[F](dbPoolThreads = 10)
    yield ServiceConfig(
      env = environment,
      keyPair = EcKeyPairConfig(privateKey, publicKey),
      db = databaseConfig
    )).load[F]
