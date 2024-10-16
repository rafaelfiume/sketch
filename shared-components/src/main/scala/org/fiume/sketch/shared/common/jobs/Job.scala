package org.fiume.sketch.shared.common.jobs

import org.fiume.sketch.shared.common.{Entity, EntityId}

import java.util.UUID

type JobId = EntityId[JobEntity]
object JobId:
  def apply(uuid: UUID): JobId = EntityId[JobEntity](uuid)
sealed trait JobEntity extends Entity

trait Job[F[_], A]:
  val description: String
  def run(): F[A]

case class JobWrapper[F[_], A](effect: F[A], val description: String) extends Job[F, A]:
  override def run(): F[A] = effect
