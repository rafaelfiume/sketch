package org.fiume.sketch.shared.common.jobs

trait Job[F[_], A]:
  val description: String
  def run(): F[A]

case class JobWrapper[F[_], A](effect: F[A], val description: String) extends Job[F, A]:
  override def run(): F[A] = effect
