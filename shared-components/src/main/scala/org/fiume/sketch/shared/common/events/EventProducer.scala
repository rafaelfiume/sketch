package org.fiume.sketch.shared.common.events

import org.fiume.sketch.shared.common.WithUuid

type EventProducer[F[_], Event] =
  GenericEventProducer[F, Event, Event & WithUuid[EventId]]

/*
 * Find the right level of abstraction is hard.
 * Keep an algebra too generic, and it might become cumbersome to use,
 * requiring complex or unintuitive type juggling.
 * Keep it too simple, and it might not be reusable enough.
 *
 * Favour the simpler `EventProducer`, since
 * in most cases, the `ProducedEvent` is simply the `Event` enriched with `EventId`.
 *
 * This reminds me of the
 * [Monads are Monoids in the Category of Endofunctors](https://www.youtube.com/watch?v=CMm98RkCgPg)
 * talk by Rock The JVM, which is definitely worth a watch (or two).
 */
trait GenericEventProducer[F[_], Event, ProducedEvent]:
  def produceEvent(event: Event): F[ProducedEvent]

/*
 * Supporting event cancellation by a parameterised `Id` (e.g. `UserId`)
 * can be convenient in cases where events' `EventId` is not available.
 * For example, a scheduled account deletion can be cancelled by `UserId`.
 *
 * However, this introduces asymmetry: `produceEvent` operates on an `Event` type
 * without requiring an `Id`, while `cancelEvent` introduces an `Id` type.
 *
 * If such an asymmetry becomes a problem, perhaps a solution could be to provide both:
 * - `cancelEvent(contextualId: Id)`: for convenience when a contextual `Id` is available
 * - `cancelEvent(uuid: EventId)`: for direct cancellation using the event's unique identifier.
 */
trait CancellableEvent[F[_], Id]:
  def cancelEvent(contextualId: Id): F[Unit]

type CancellableEventProducer[F[_], Event, Id] = EventProducer[F, Event] & CancellableEvent[F, Id]
