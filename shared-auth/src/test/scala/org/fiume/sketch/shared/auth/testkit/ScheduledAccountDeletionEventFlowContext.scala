package org.fiume.sketch.shared.auth.testkit

import cats.effect.IO
import cats.implicits.*
import org.fiume.sketch.shared.auth.UserId
import org.fiume.sketch.shared.auth.accounts.jobs.{
  AccountDeletionEvent,
  AccountDeletionEventConsumer,
  AccountDeletionEventProducer
}
import org.fiume.sketch.shared.auth.accounts.jobs.AccountDeletionEvent.*
import org.fiume.sketch.shared.common.jobs.JobId

object ScheduledAccountDeletionEventFlowContext:

  trait EventProducerContext:
    private case class State(events: Map[JobId, AccountDeletionEvent.Scheduled]):
      def +++(event: AccountDeletionEvent.Scheduled): State =
        copy(events = events + (event.uuid -> event))

      def ---(userId: UserId): State =
        events
          .collectFirst {
            case (jobId, event) if event.userId == userId => jobId
          }
          .fold(this)(jobId => copy(events = events - jobId))

      def getEvent(userId: UserId): Option[AccountDeletionEvent.Scheduled] =
        events.collectFirst {
          case (_, event) if event.userId == userId => event
        }

    private object State:
      val empty = State(Map.empty)

    def makeEventProducer(): IO[AccountDeletionEventProducer[IO] & EventsInspector] = makeEventProducer(
      State.empty
    )

    private def makeEventProducer(state: State): IO[AccountDeletionEventProducer[IO] & EventsInspector] =
      IO.ref(state).map { storage =>
        new AccountDeletionEventProducer[IO] with EventsInspector:
          override def produceEvent(event: AccountDeletionEvent.Unscheduled): IO[AccountDeletionEvent.Scheduled] =
            for
              jobId <- IO.randomUUID.map(JobId(_))
              job = AccountDeletionEvent.scheduled(jobId, event.userId, event.permanentDeletionAt)
              _ <- storage.update { _.+++(job) }
            yield job

          override def removeEvent(uuid: UserId): IO[Unit] = storage.update { _.---(uuid) }.void

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
          override def claimNextJob(): IO[Option[AccountDeletionEvent.Scheduled]] = state.getAndSet(State.empty).map(_.event)
      }

  trait EventsInspector:
    def inspectProducedEvent(userId: UserId): IO[Option[AccountDeletionEvent.Scheduled]]
