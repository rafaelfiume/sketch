package org.fiume.sketch.app

import cats.effect.Async
import cats.implicits.*
import ciris.*
import com.comcast.ip4s.{Host, Port}
import org.fiume.sketch.auth0.KeyStringifier
import org.fiume.sketch.profile.ProfileClientConfig
import org.fiume.sketch.shared.app.Version.Environment
import org.fiume.sketch.storage.DatabaseConfig
import org.http4s.headers.Origin

import java.security.interfaces.{ECPrivateKey, ECPublicKey}

case class ServiceConfig(
  env: Environment,
  endpoints: EndpointsConfig,
  keyPair: EcKeyPairConfig,
  db: DatabaseConfig,
  profileClient: ProfileClientConfig
)

case class EndpointsConfig(
  port: Port,
  cors: CorsAllowOrigins,
  requestResponseLoggingEnabled: Boolean,
  documents: DocumentsConfig
)

case class CorsAllowOrigins(allowedOrigins: Set[Origin])

case class DocumentsConfig(documentMbSizeLimit: Int):
  val documentBytesSizeLimit = documentMbSizeLimit * 1024 * 1024

case class EcKeyPairConfig(privateKey: ECPrivateKey, publicKey: ECPublicKey)

object ServiceConfig:
  def load[F[_]: Async]: F[ServiceConfig] =
    (for
      environment <- env("ENV").as[Environment]
      port <- env("HTTP_SERVER_PORT").as[Port]
      allowedOrigins <- env("HTTP_CORS_ALLOWS_ORIGIN").as[Set[Origin]]
      requestResponseLoggingEnabled <- env("HTTP_REQ_RES_LOG_ENABLED").as[Boolean]
      documentMbSizeLimit <- env("DOCUMENT_MB_SIZE_LIMIT").as[Int]
      privateKey <- env("PRIVATE_KEY").as[ECPrivateKey].redacted
      publicKey <- env("PUBLIC_KEY").as[ECPublicKey].redacted
      databaseConfig <- DatabaseConfig.envs[F](dbPoolThreads = 10)
      profileHost <- env("PROFILE_SERVICE_HOST").as[Host]
      profilePort <- env("PROFILE_SERVICE_PORT").as[Port]
    yield ServiceConfig(
      env = environment,
      endpoints = EndpointsConfig(
        port,
        cors = CorsAllowOrigins(allowedOrigins),
        requestResponseLoggingEnabled,
        documents = DocumentsConfig(documentMbSizeLimit)
      ),
      keyPair = EcKeyPairConfig(privateKey, publicKey),
      db = databaseConfig,
      profileClient = ProfileClientConfig(profileHost, profilePort)
    )).load[F]

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
