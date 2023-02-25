package org.fiume.sketch.storage

import ciris.*

object Config:

  case class DatabaseConfig(
    driver: String,
    url: String,
    user: String,
    password: Secret[String],
    // Match the default size of the Hikari pool
    // Also https://github.com/brettwooldridge/HikariCP/wiki/About-Pool-Sizing
    dbPoolThreads: Int = 10
  )
