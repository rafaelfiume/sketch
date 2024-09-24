package org.fiume.sketch.shared.jobs

import org.fiume.sketch.shared.app.{Entity, EntityId}

import java.util.UUID

type JobId = EntityId[Job]
object JobId:
  def apply(uuid: UUID): JobId = EntityId[Job](uuid)
sealed trait Job extends Entity
