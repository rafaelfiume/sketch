package org.fiume.sketch.postgres

import cats.data.NonEmptyList
import cats.implicits.*
import doobie.*
import doobie.implicits.*
import org.fiume.sketch.postgres.*

import java.time.ZonedDateTime

object Statements:
  val healthCheck: ConnectionIO[Int] = sql"select 42".query[Int].unique
