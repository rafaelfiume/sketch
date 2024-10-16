package org.fiume.sketch.shared.common.http.json

import io.circe.Encoder
import org.fiume.sketch.shared.common.{Entity, EntityId}
import org.fiume.sketch.shared.common.EntityId.given

object JsonCodecs:

  given [T <: Entity]: Encoder[EntityId[T]] = Encoder[String].contramap[EntityId[T]](_.asString())
