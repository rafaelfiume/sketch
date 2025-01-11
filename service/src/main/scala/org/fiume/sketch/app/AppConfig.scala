package org.fiume.sketch.app

import cats.effect.{Async, Resource, Sync}
import cats.implicits.*
import ciris.*
import com.comcast.ip4s.*
import doobie.ConnectionIO
import org.fiume.sketch.auth.KeyStringifier
import org.fiume.sketch.rustic.RusticClientConfig
import org.fiume.sketch.shared.auth.accounts.AccountConfig
import org.fiume.sketch.shared.common.app.Version.Environment
import org.fiume.sketch.shared.common.config.{DynamicConfig, Namespace}
import org.fiume.sketch.storage.config.postgres.PostgresDynamicConfigStore
import org.fiume.sketch.storage.postgres.DatabaseConfig
import org.http4s.headers.Origin

import java.security.interfaces.{ECPrivateKey, ECPublicKey}
import scala.concurrent.duration.*

object AppConfig:

  case class Static(
    env: Environment,
    endpoints: EndpointsConfig,
    keyPair: EcKeyPairConfig,
    db: DatabaseConfig,
    rusticClient: RusticClientConfig,
    account: AccountConfig,
    documents: DocumentsConfig
  )

  case class EndpointsConfig(
    port: Port,
    cors: CorsAllowOrigins,
    requestResponseLoggingEnabled: Boolean
  )

  case class CorsAllowOrigins(allowedOrigins: Set[Origin])

  case class DocumentsConfig(documentMbSizeLimit: Int):
    val documentBytesSizeLimit = documentMbSizeLimit * 1024 * 1024

  case class EcKeyPairConfig(privateKey: ECPrivateKey, publicKey: ECPublicKey)

  def makeFromEnvs[F[_]: Async](): F[Static] =
    (for
      environment <- env("ENV").as[Environment]
      port <- env("HTTP_SERVER_PORT").as[Port]
      allowedOrigins <- env("HTTP_CORS_ALLOWS_ORIGIN").as[Set[Origin]]
      requestResponseLoggingEnabled <- env("HTTP_REQ_RES_LOG_ENABLED").as[Boolean]
      documentMbSizeLimit <- env("DOCUMENT_MB_SIZE_LIMIT").as[Int]
      privateKey <- env("PRIVATE_KEY").as[ECPrivateKey].redacted
      publicKey <- env("PUBLIC_KEY").as[ECPublicKey].redacted
      databaseConfig <- DatabaseConfig.envs[F](dbPoolThreads = 10)
      rusticHost <- env("RUSTIC_SKETCH_HOST").as[Host]
      rusticPort <- env("RUSTIC_SKETCH_PORT").as[Port]
    yield Static(
      env = environment,
      endpoints = EndpointsConfig(
        port,
        cors = CorsAllowOrigins(allowedOrigins),
        requestResponseLoggingEnabled
      ),
      keyPair = EcKeyPairConfig(privateKey, publicKey),
      db = databaseConfig,
      rusticClient = RusticClientConfig(rusticHost, rusticPort),
      // TODO Load from the environment
      account = AccountConfig(
        delayUntilPermanentDeletion = 90.days,
        permanentDeletionJobInterval = 1.minute // consider to increase this interval
      ),
      documents = DocumentsConfig(documentMbSizeLimit)
    )).load[F]

  def makeDynamicConfig[F[_]: Sync](): Resource[F, DynamicConfig[ConnectionIO]] =
    PostgresDynamicConfigStore.makeForNamespace[F](Namespace("sketch")) // see system.dynamic_configs table

  given ConfigDecoder[String, Environment] = ConfigDecoder[String].map(Environment.apply)

  given ConfigDecoder[String, Host] = ConfigDecoder[String].mapOption("Host")(Host.fromString)
  given ConfigDecoder[String, Port] = ConfigDecoder[String].mapOption("Port")(Port.fromString)

  given ConfigDecoder[String, Set[Origin]] = ConfigDecoder[String]
    .map(_.split('|').toList)
    .mapEither((_, uris) => uris.traverse(uri => Origin.parse(uri).leftMap(e => ConfigError(e.details))))
    .map(_.toSet)

  given ConfigDecoder[String, ECPrivateKey] =
    ConfigDecoder[String].mapEither((_, key) => KeyStringifier.ecPrivateKeyFromPem(key).leftMap(ConfigError(_)))

  given ConfigDecoder[String, ECPublicKey] =
    ConfigDecoder[String].mapEither((_, key) => KeyStringifier.ecPublicKeyFromPem(key).leftMap(ConfigError(_)))
