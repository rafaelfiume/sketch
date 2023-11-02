package org.fiume.sketch.app

import cats.effect.Async
import cats.implicits.*
import ciris.*
import com.comcast.ip4s.Port
import org.fiume.sketch.auth0.KeyStringifier
import org.fiume.sketch.shared.app.algebras.Versions.Environment
import org.fiume.sketch.storage.DatabaseConfig

import java.security.interfaces.{ECPrivateKey, ECPublicKey}

case class ServiceConfig(
  env: Environment,
  endpoints: EndpointsConfig,
  keyPair: EcKeyPairConfig,
  db: DatabaseConfig
)

case class EndpointsConfig(
  port: Port,
  requestResponseLoggingEnabled: Boolean,
  documents: DocumentsConfig
)

case class DocumentsConfig(documentMbSizeLimit: Int):
  val documentBytesSizeLimit = documentMbSizeLimit * 1024 * 1024

case class EcKeyPairConfig(privateKey: ECPrivateKey, publicKey: ECPublicKey)

object ServiceConfig:
  given ConfigDecoder[String, Environment] = ConfigDecoder[String].map(Environment.apply)
  given ConfigDecoder[String, Port] = ConfigDecoder[String].mapOption("Port")(Port.fromString)
  given ConfigDecoder[String, ECPrivateKey] =
    ConfigDecoder[String].mapEither((_, key) => KeyStringifier.ecPrivateKeyFromPem(key).leftMap(ConfigError(_)))
  given ConfigDecoder[String, ECPublicKey] =
    ConfigDecoder[String].mapEither((_, key) => KeyStringifier.ecPublicKeyFromPem(key).leftMap(ConfigError(_)))

  def load[F[_]: Async]: F[ServiceConfig] =
    (for
      environment <- env("ENV").as[Environment]
      port <- env("HTTP_SERVER_PORT").as[Port]
      requestResponseLoggingEnabled <- env("HTTP_REQ_RES_LOG_ENABLED").as[Boolean]
      documentMbSizeLimit <- env("DOCUMENT_MB_SIZE_LIMIT").as[Int]
      privateKey <- env("PRIVATE_KEY").as[ECPrivateKey].redacted
      publicKey <- env("PUBLIC_KEY").as[ECPublicKey].redacted
      databaseConfig <- DatabaseConfig.envs[F](dbPoolThreads = 10)
    yield ServiceConfig(
      env = environment,
      endpoints = EndpointsConfig(
        port,
        requestResponseLoggingEnabled,
        documents = DocumentsConfig(documentMbSizeLimit)
      ),
      keyPair = EcKeyPairConfig(privateKey, publicKey),
      db = databaseConfig
    )).load[F]
