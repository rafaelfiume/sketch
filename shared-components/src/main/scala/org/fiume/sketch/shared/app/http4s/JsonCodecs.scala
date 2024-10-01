package org.fiume.sketch.shared.app.http4s

import io.circe.Encoder
import org.fiume.sketch.shared.app.{Entity, EntityId}
import org.fiume.sketch.shared.app.EntityId.given

object JsonCodecs:

  given [T <: Entity]: Encoder[EntityId[T]] = Encoder[String].contramap[EntityId[T]](_.asString())
