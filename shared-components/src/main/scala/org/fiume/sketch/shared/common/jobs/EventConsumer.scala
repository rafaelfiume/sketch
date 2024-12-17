package org.fiume.sketch.shared.common.jobs

trait EventConsumer[F[_], A]:
  /**
   * Claims the next available event from the lightweight event bus
   */
  def claimNextJob(): F[Option[A]]
