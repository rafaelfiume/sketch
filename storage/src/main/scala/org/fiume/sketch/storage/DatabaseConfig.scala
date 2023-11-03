package org.fiume.sketch.storage

import ciris.*
import org.http4s.Uri

case class DatabaseConfig(
  driver: String,
  host: String,
  port: Int,
  name: String,
  user: String,
  password: Secret[String],
  dbPoolThreads: Int
):
  def jdbcUri: Uri = Uri.unsafeFromString(s"jdbc:postgresql://$host:$port/$name")

object DatabaseConfig:

  // For `dbPoolThreads`, see https://github.com/brettwooldridge/HikariCP/wiki/About-Pool-Sizing#the-formula
  def envs[F[_]](dbPoolThreads: Int): ConfigValue[F, DatabaseConfig] =
    for
      host <- env("DB_HOST")
      port <- env("DB_PORT").as[Int]
      name <- env("DB_NAME")
      dbUser <- env("DB_USER")
      dbPassword <- env("DB_PASS").secret
    yield DatabaseConfig(
      driver = "org.postgresql.Driver",
      host = host,
      port = port,
      name = name,
      user = dbUser,
      password = dbPassword,
      dbPoolThreads = dbPoolThreads
    )
