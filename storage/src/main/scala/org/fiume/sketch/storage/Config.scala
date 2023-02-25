package org.fiume.sketch.storage

import ciris.*

object Config:

  case class DatabaseConfig(
    driver: String,
    url: String,
    user: String,
    password: Secret[String]
  )
