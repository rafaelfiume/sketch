package org.fiume.sketch.shared.auth.testkit

import cats.effect.IO
import cats.implicits.*
import org.fiume.sketch.shared.auth.accounts.AccountDeletionEvent.*
import org.fiume.sketch.shared.common.WithUuid
import org.fiume.sketch.shared.common.events.{CancellableEvent, CancellableEventProducer, EventConsumer, EventId, EventProducer}

type Enriched[Event] = Event & WithUuid[EventId]
type Enrich[Event] = (Event, EventId) => Enriched[Event]
type ExtractContextualId[Event, Id] = Event => Id

object EventsFlowContext:

  trait CancellableEventProducerContext[Event, Id]:
    private case class State(events: Map[EventId, Enriched[Event]], extractId: ExtractContextualId[Event, Id]):
      def +++(event: Enriched[Event]): State =
        copy(events = events + (event.uuid -> event))

      def ---(id: Id): State =
        events
          .collectFirst {
            case (eventId, event) if extractId(event) == id => eventId
          }
          .fold(this)(eventId => copy(events = events - eventId))

      def getEvent(id: Id): Option[Enriched[Event]] =
        events.collectFirst {
          case (_, event) if extractId(event) == id => event
        }

    def makeEventProducer(
      enrich: Enrich[Event],
      extractId: ExtractContextualId[Event, Id]
    ): IO[CancellableEventProducer[IO, Event, Id] & EventsInspector[Event, Id]] =
      makeEventProducer(State(Map.empty, extractId), enrich)

    private def makeEventProducer(
      state: State,
      enrich: Enrich[Event]
    ): IO[CancellableEventProducer[IO, Event, Id] & EventsInspector[Event, Id]] =
      IO.ref(state).map { storage =>
        new EventProducer[IO, Event] with CancellableEvent[IO, Id] with EventsInspector[Event, Id]:
          override def produceEvent(event: Event): IO[Enriched[Event]] =
            for
              eventId <- IO.randomUUID.map(EventId(_))
              enriched = enrich(event, eventId)
              _ <- storage.update { _.+++(enriched) }
            yield enriched

          override def cancelEventById(id: Id): IO[Unit] =
            storage.update { _.---(id) }.void

          override def inspectProducedEvent(id: Id): IO[Option[Enriched[Event]]] =
            storage.get.map(_.getEvent(id))
      }

  trait EventConsumerContext[Event]:
    private case class State(event: Option[Enriched[Event]])

    def makeEmptyEventConsumer(): IO[EventConsumer[IO, Enriched[Event]]] = makeEventConsumer(none)

    def makeEventConsumer(nextEvent: Enriched[Event]): IO[EventConsumer[IO, Enriched[Event]]] =
      makeEventConsumer(nextEvent.some)

    private def makeEventConsumer(event: Option[Enriched[Event]]): IO[EventConsumer[IO, Enriched[Event]]] =
      IO.ref(State(event)).map { state =>
        new EventConsumer[IO, Enriched[Event]]:
          override def consumeEvent(): IO[Option[Enriched[Event]]] = state.getAndSet(State(none)).map(_.event)
      }

  trait EventsInspector[Event, Id]:
    def inspectProducedEvent(id: Id): IO[Option[Enriched[Event]]]
