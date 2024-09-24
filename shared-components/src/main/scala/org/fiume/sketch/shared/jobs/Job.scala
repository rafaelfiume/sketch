package org.fiume.sketch.shared.jobs

import org.fiume.sketch.shared.app.{Entity, EntityId}

import java.util.UUID

type JobId = EntityId[JobEntity]
object JobId:
  def apply(uuid: UUID): JobId = EntityId[JobEntity](uuid)
sealed trait JobEntity extends Entity

trait Job[F[_], A]:
  def run(): F[A]
  val description: String

case class JobWrapper[F[_], A](
  effect: F[A],
  val description: String
) extends Job[F, A]:
  override def run(): F[A] = effect
