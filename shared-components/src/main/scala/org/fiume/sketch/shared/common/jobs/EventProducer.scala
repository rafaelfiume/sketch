package org.fiume.sketch.shared.common.jobs

trait EventProducer[F[_], Event, Outcome, Id]:
  def produceEvent(event: Event): F[Outcome]
  def removeEvent(id: Id): F[Unit]
