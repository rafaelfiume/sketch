package org.fiume.sketch.shared.common.events

trait EventConsumer[F[_], A]:
  def consumeEvent(): F[Option[A]]
