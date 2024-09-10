package org.fiume.sketch.storage.authorisation.postgres

import cats.implicits.*
import doobie.Meta
import doobie.postgres.implicits.*
import org.fiume.sketch.authorisation.{ContextualRole, GlobalRole, Role}
import org.fiume.sketch.authorisation.Role.given
import org.fiume.sketch.shared.app.{Entity, EntityId}

import java.util.UUID

private[storage] object DoobieMappings:

  given Meta[GlobalRole] = Meta[String].timap(GlobalRole.valueOf(_))(_.toString)

  given Meta[ContextualRole] = Meta[String].timap(ContextualRole.valueOf(_))(_.toString)

  given Meta[Role] = Meta[String].tiemap(_.parsed().leftMap(_.message))(_.asString())

  // TODO Move it to shared app package?
  given meta[T <: Entity]: Meta[EntityId[T]] = Meta[UUID].timap(EntityId(_))(_.value)
