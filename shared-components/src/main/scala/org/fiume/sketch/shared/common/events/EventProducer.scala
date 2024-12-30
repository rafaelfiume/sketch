package org.fiume.sketch.shared.common.events

import org.fiume.sketch.shared.common.WithUuid

type EventProducer[F[_], Event, Id] =
  GenericEventProducer[F, Event, Event & WithUuid[EventId], Id]

/*
 * Find the right level of abstraction is hard.
 * Keep an algebra too generic, and it might become cumbersome to use,
 * requiring complex or unintuitive type juggling.
 * Keep it too simple, and it might not be reusable enough.
 *
 * Favour the simpler `EventProducer`, since
 * in most cases, the `ProducedEvent` is simply the `Event` enriched with `EventId`.
 */
trait GenericEventProducer[F[_], Event, ProducedEvent, Id]:
  def produceEvent(event: Event): F[ProducedEvent]
  def removeEvent(id: Id): F[Unit]
