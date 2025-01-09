package org.fiume.sketch.storage.config.postgres

import cats.implicits.*
import doobie.util.meta.Meta
import io.circe.Json
import org.fiume.sketch.shared.common.config.Namespace
import org.postgresql.util.PGobject

private[storage] object DatabaseCodecs:

  given Meta[Namespace] = Meta[String].timap(Namespace(_))(_.value)

  // TODO Move it to a common place
  given Meta[Json] =
    Meta.Advanced
      .other[PGobject]("json")
      .timap[Json](a => io.circe.parser.parse(a.getValue).leftMap[Json](e => throw e).merge)(a =>
        val o = new PGobject
        o.setType("json")
        o.setValue(a.noSpaces)
        o
      )
