package org.fiume.sketch.app

import cats.effect.Async
import cats.implicits.*
import ciris.*
import org.fiume.sketch.app.ServiceConfig.*
import org.fiume.sketch.shared.app.algebras.Versions.Environment
import org.fiume.sketch.storage.Config.DatabaseConfig
import org.http4s.Uri

import scala.concurrent.duration.*

case class ServiceConfig(
  env: Environment,
  db: DatabaseConfig
)

object ServiceConfig:
  given ConfigDecoder[String, Uri] = ConfigDecoder[String].map(Uri.unsafeFromString)
  given ConfigDecoder[String, Environment] = ConfigDecoder[String].map(Environment.apply)

  def load[F[_]: Async]: F[ServiceConfig] =
    (for
      environment <- env("ENV").as[Environment]
      jdbcUrl <- env("DB_URL").as[Uri]
      dbUser <- env("DB_USER")
      dbPassword <- env("DB_PASS").secret
    yield ServiceConfig(
      env = environment,
      db = DatabaseConfig(
        driver = "org.postgresql.Driver",
        uri = jdbcUrl,
        user = dbUser,
        password = dbPassword
      )
    )).load[F]
