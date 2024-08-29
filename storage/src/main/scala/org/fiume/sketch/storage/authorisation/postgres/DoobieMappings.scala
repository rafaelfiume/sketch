package org.fiume.sketch.storage.authorisation.postgres

import doobie.Meta
import doobie.postgres.implicits.*
import org.fiume.sketch.authorisation.Role
import org.fiume.sketch.shared.app.{Resource, ResourceId}

import java.util.UUID

private[storage] object DoobieMappings:

  given Meta[Role] = Meta[String].timap(Role.valueOf(_))(_.toString)

  given meta[T <: Resource]: Meta[ResourceId[T]] = Meta[UUID].timap(ResourceId(_))(_.value)
