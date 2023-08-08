package org.fiume.sketch.storage

import ciris.*
import org.http4s.Uri

case class DatabaseConfig(
  driver: String,
  uri: Uri,
  user: String,
  password: Secret[String],
  // Match the default size of the Hikari pool
  // Also https://github.com/brettwooldridge/HikariCP/wiki/About-Pool-Sizing
  dbPoolThreads: Int = 10
)
