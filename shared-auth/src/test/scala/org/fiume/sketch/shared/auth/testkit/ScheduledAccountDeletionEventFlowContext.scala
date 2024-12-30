package org.fiume.sketch.shared.auth.testkit

import cats.effect.IO
import cats.implicits.*
import org.fiume.sketch.shared.auth.UserId
import org.fiume.sketch.shared.auth.accounts.{
  AccountDeletionEvent,
  AccountDeletionEventConsumer,
  CancellableAccountDeletionEventProducer
}
import org.fiume.sketch.shared.auth.accounts.AccountDeletionEvent.*
import org.fiume.sketch.shared.common.events.EventId
object ScheduledAccountDeletionEventFlowContext:

  trait EventProducerContext:
    private case class State(events: Map[EventId, AccountDeletionEvent.Scheduled]):
      def +++(event: AccountDeletionEvent.Scheduled): State =
        copy(events = events + (event.uuid -> event))

      def ---(userId: UserId): State =
        events
          .collectFirst {
            case (eventId, event) if event.userId == userId => eventId
          }
          .fold(this)(eventId => copy(events = events - eventId))

      def getEvent(userId: UserId): Option[AccountDeletionEvent.Scheduled] =
        events.collectFirst {
          case (_, event) if event.userId == userId => event
        }

    private object State:
      val empty = State(Map.empty)

    def makeEventProducer(): IO[CancellableAccountDeletionEventProducer[IO] & EventsInspector] = makeEventProducer(
      State.empty
    )

    private def makeEventProducer(state: State): IO[CancellableAccountDeletionEventProducer[IO] & EventsInspector] =
      IO.ref(state).map { storage =>
        new CancellableAccountDeletionEventProducer[IO] with EventsInspector:
          override def produceEvent(event: AccountDeletionEvent.ToSchedule): IO[AccountDeletionEvent.Scheduled] =
            for
              eventId <- IO.randomUUID.map(EventId(_))
              scheduled = AccountDeletionEvent.scheduled(eventId, event.userId, event.permanentDeletionAt)
              _ <- storage.update { _.+++(scheduled) }
            yield scheduled

          override def cancelEventById(uuid: UserId): IO[Unit] = storage.update { _.---(uuid) }.void

          override def inspectProducedEvent(userId: UserId): IO[Option[AccountDeletionEvent.Scheduled]] =
            storage.get.map(_.getEvent(userId))
      }

  trait EventConsumerContext:
    private case class State(event: Option[AccountDeletionEvent.Scheduled])

    private object State:
      val empty = State(none)

    def makeEmptyEventConsumer(): IO[AccountDeletionEventConsumer[IO]] = makeEventConsumer(none)

    def makeEventConsumer(nextEvent: AccountDeletionEvent.Scheduled): IO[AccountDeletionEventConsumer[IO]] =
      makeEventConsumer(nextEvent.some)

    private def makeEventConsumer(event: Option[AccountDeletionEvent.Scheduled]): IO[AccountDeletionEventConsumer[IO]] =
      IO.ref(State(event)).map { state =>
        new AccountDeletionEventConsumer[IO]:
          override def consumeEvent(): IO[Option[AccountDeletionEvent.Scheduled]] = state.getAndSet(State.empty).map(_.event)
      }

  trait EventsInspector:
    def inspectProducedEvent(userId: UserId): IO[Option[AccountDeletionEvent.Scheduled]]
