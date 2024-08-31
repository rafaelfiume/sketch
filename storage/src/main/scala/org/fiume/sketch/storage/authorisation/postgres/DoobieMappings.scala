package org.fiume.sketch.storage.authorisation.postgres

import doobie.Meta
import doobie.postgres.implicits.*
import org.fiume.sketch.authorisation.Role
import org.fiume.sketch.shared.app.{Entity, EntityId}

import java.util.UUID

private[storage] object DoobieMappings:

  given Meta[Role] = Meta[String].timap(Role.valueOf(_))(_.toString)

  // TODO Move it to shared app package?
  given meta[T <: Entity]: Meta[EntityId[T]] = Meta[UUID].timap(EntityId(_))(_.value)
