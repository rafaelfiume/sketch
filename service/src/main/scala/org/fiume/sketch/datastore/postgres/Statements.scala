package org.fiume.sketch.datastore.postgres

import cats.data.NonEmptyList
import cats.implicits.*
import doobie.*
import doobie.implicits.*

import java.time.ZonedDateTime

object Statements:
  val healthCheck: ConnectionIO[Int] = sql"select 42".query[Int].unique
