package org.fiume.sketch.shared.auth.testkit

import cats.effect.IO
import cats.effect.kernel.Ref
import cats.implicits.*
import org.fiume.sketch.shared.common.WithUuid
import org.fiume.sketch.shared.common.events.{CancellableEvent, CancellableEventProducer, EventConsumer, EventId, EventProducer}

object EventsFlowContext:
  type EnrichedEvent[E] = E & WithUuid[EventId]
  type Enricher[E] = (E, EventId) => EnrichedEvent[E]
  type EventContextualIdExtractor[E, Id] = E => Id

  trait EventProducerContext[E]:
    def makeEventProducer(enrich: Enricher[E]): IO[EventProducer[IO, E] & EventInspector] =
      val state = EventProducerState[E](Map.empty)
      IO.ref(state).map { new FakeEventProducer(_, enrich) }

  trait CancellableEventProducerContext[E, Id]:
    def makeCancellableEventProducer(
      enrich: Enricher[E],
      extractId: EventContextualIdExtractor[E, Id]
    ): IO[CancellableEventProducer[IO, E, Id] & CancellableEventInspector[E, Id]] =
      val state = CancellableEventProducerState(Map.empty, extractId)
      IO.ref(state).map { new FakeCancellableEventProducer(_, enrich) }

  trait EventConsumerContext[E]:
    def makeEmptyEventConsumer(): IO[EventConsumer[IO, EnrichedEvent[E]]] = makeEventConsumer(event = none)

    def makeEventConsumer(nextEvent: EnrichedEvent[E]): IO[EventConsumer[IO, EnrichedEvent[E]]] =
      makeEventConsumer(nextEvent.some)

    private def makeEventConsumer(event: Option[EnrichedEvent[E]]): IO[EventConsumer[IO, EnrichedEvent[E]]] =
      IO.ref(State(event)).map { state =>
        new EventConsumer[IO, EnrichedEvent[E]]:
          override def consumeEvent(): IO[Option[EnrichedEvent[E]]] = state.getAndSet(State(none)).map(_.event)
      }

    private case class State(event: Option[EnrichedEvent[E]])

  trait EventInspector:
    def totalSent(): IO[Int]

  trait CancellableEventInspector[E, Id]:
    def inspectProducedEvent(contextualId: Id): IO[Option[EnrichedEvent[E]]]

  sealed trait BaseFakeEventProducer[E, S <: BaseEventProducerState[E, S]](storage: Ref[IO, S], enrich: Enricher[E])
      extends EventProducer[IO, E]:
    def produceEvent(event: E): IO[EnrichedEvent[E]] =
      for
        eventId <- IO.randomUUID.map(EventId(_))
        enriched = enrich(event, eventId)
        _ <- storage.update { _.+++(enriched) }
      yield enriched

  private class FakeEventProducer[E](
    storage: Ref[IO, EventProducerState[E]],
    enrich: Enricher[E]
  ) extends BaseFakeEventProducer[E, EventProducerState[E]](storage, enrich)
      with EventInspector:
    override def totalSent(): IO[Int] = storage.get.map(_.events.size)

  private class FakeCancellableEventProducer[E, Id](
    storage: Ref[IO, CancellableEventProducerState[E, Id]],
    enrich: Enricher[E]
  ) extends BaseFakeEventProducer[E, CancellableEventProducerState[E, Id]](storage, enrich)
      with CancellableEvent[IO, Id]
      with CancellableEventInspector[E, Id]:

    override def cancelEvent(contextualId: Id): IO[Unit] = storage.update { _.---(contextualId) }.void

    override def inspectProducedEvent(contextualId: Id): IO[Option[EnrichedEvent[E]]] =
      storage.get.map(_.getEventByContextualId(contextualId))

  // Why `Fake`? For example, `FakeEventProducer` is a fully working implementation
  // that mimicks the behaviour of a real component and is intended exclusively for testing purposes.
  // It allows for state manipulation and inspection to test the behaviour of components
  // that depend on an `EventProducer` without relying on production machinery.

  sealed trait BaseEventProducerState[E, Self <: BaseEventProducerState[E, Self]]:
    def +++(event: EnrichedEvent[E]): Self = modifyEvents(events = events + (event.uuid -> event))
    protected val events: Map[EventId, EnrichedEvent[E]]
    protected def modifyEvents(events: Map[EventId, EnrichedEvent[E]]): Self

  private case class EventProducerState[E](events: Map[EventId, EnrichedEvent[E]])
      extends BaseEventProducerState[E, EventProducerState[E]]:
    override protected def modifyEvents(events: Map[EventId, EnrichedEvent[E]]) = copy(events = events)

  private case class CancellableEventProducerState[E, Id](
    events: Map[EventId, EnrichedEvent[E]],
    extractId: EventContextualIdExtractor[E, Id]
  ) extends BaseEventProducerState[E, CancellableEventProducerState[E, Id]]:
    def ---(id: Id): CancellableEventProducerState[E, Id] =
      events
        .collectFirst {
          case (eventId, event) if extractId(event) == id => eventId
        }
        .fold(this)(eventId => copy(events = events - eventId))

    def getEventByContextualId(id: Id): Option[EnrichedEvent[E]] =
      events.collectFirst {
        case (_, event) if extractId(event) == id => event
      }

    override protected def modifyEvents(events: Map[EventId, EnrichedEvent[E]]) = copy(events = events)
