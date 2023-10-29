package org.fiume.sketch.storage

import ciris.*
import org.http4s.Uri

case class DatabaseConfig(
  driver: String,
  uri: Uri,
  user: String,
  password: Secret[String],
  dbPoolThreads: Int
)

object DatabaseConfig:
  given ConfigDecoder[String, Uri] = ConfigDecoder[String].map(Uri.unsafeFromString)

  // For `dbPoolThreads`, see https://github.com/brettwooldridge/HikariCP/wiki/About-Pool-Sizing#the-formula
  def load[F[_]](dbPoolThreads: Int): ConfigValue[F, DatabaseConfig] =
    for
      jdbcUrl <- env("DB_URL").as[Uri]
      dbUser <- env("DB_USER")
      dbPassword <- env("DB_PASS").secret
    yield DatabaseConfig(
        driver = "org.postgresql.Driver",
        uri = jdbcUrl,
        user = dbUser,
        password = dbPassword,
        dbPoolThreads = dbPoolThreads
    )
