package org.fiume.sketch.shared.common.events

import org.fiume.sketch.shared.common.{Entity, EntityId}

import java.util.UUID

type EventId = EntityId[EventEntity]
object EventId:
  def apply(uuid: UUID): EventId = EntityId[EventEntity](uuid)
sealed trait EventEntity extends Entity
