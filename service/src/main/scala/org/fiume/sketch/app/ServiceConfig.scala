package org.fiume.sketch.app

import cats.effect.Async
import cats.implicits.*
import ciris.*
import org.fiume.sketch.app.ServiceConfig.*
import org.fiume.sketch.auth0.KeyStringifier
import org.fiume.sketch.shared.app.algebras.Versions.Environment
import org.fiume.sketch.storage.Config.DatabaseConfig
import org.http4s.Uri

import java.security.interfaces.{ECPrivateKey, ECPublicKey}
import scala.concurrent.duration.*

case class ServiceConfig(
  env: Environment,
  db: DatabaseConfig
)

object ServiceConfig:
  given ConfigDecoder[String, Uri] = ConfigDecoder[String].map(Uri.unsafeFromString)
  given ConfigDecoder[String, Environment] = ConfigDecoder[String].map(Environment.apply)
  given ConfigDecoder[String, ECPrivateKey] =
    ConfigDecoder[String].mapEither((_, key) => KeyStringifier.ecPrivateKeyFromPem(key).leftMap(ConfigError(_)))
  given ConfigDecoder[String, ECPublicKey] =
    ConfigDecoder[String].mapEither((_, key) => KeyStringifier.ecPublicKeyFromPem(key).leftMap(ConfigError(_)))

  def load[F[_]: Async]: F[ServiceConfig] =
    (for
      environment <- env("ENV").as[Environment]
      jdbcUrl <- env("DB_URL").as[Uri]
      dbUser <- env("DB_USER")
      dbPassword <- env("DB_PASS").secret
      privateKey <- env("PRIVATE_KEY").as[ECPrivateKey].redacted
      publicKey <- env("PUBLIC_KEY").as[ECPublicKey].redacted
    yield ServiceConfig(
      env = environment,
      db = DatabaseConfig(
        driver = "org.postgresql.Driver",
        uri = jdbcUrl,
        user = dbUser,
        password = dbPassword,
        dbPoolThreads = 10
      )
    )).load[F]
