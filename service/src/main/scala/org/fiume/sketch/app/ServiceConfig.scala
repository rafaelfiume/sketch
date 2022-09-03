package org.fiume.sketch.app

import cats.effect.Async
import cats.implicits.*
import ciris.*
import org.fiume.sketch.app.ServiceConfig.*
import org.http4s.Uri

import scala.concurrent.duration.*

case class ServiceConfig(
  env: String,
  db: DatabaseConfig
)

object ServiceConfig:
  given ConfigDecoder[String, Uri] = ConfigDecoder[String].map(Uri.unsafeFromString)

  case class DatabaseConfig(driver: String, url: String, user: String, password: Secret[String])

  def load[F[_]: Async]: F[ServiceConfig] =
    (for
      environment <- env("ENV")
      jdbcUrl <- env("DB_URL")
      dbUser <- env("DB_USER")
      dbPassword <- env("DB_PASS").secret
    yield ServiceConfig(
      environment,
      db = DatabaseConfig(
        driver = "org.postgresql.Driver",
        url = jdbcUrl,
        user = dbUser,
        password = dbPassword
      )
    )).load[F]
