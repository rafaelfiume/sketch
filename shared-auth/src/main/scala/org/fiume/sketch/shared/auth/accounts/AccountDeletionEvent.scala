package org.fiume.sketch.shared.auth.accounts

import org.fiume.sketch.shared.auth.UserId
import org.fiume.sketch.shared.common.WithUuid
import org.fiume.sketch.shared.common.events.{EventConsumer, EventId, EventProducer}

import java.time.Instant

type AccountDeletionEventProducer[F[_]] =
  EventProducer[F, AccountDeletionEvent.ToSchedule, UserId]

type AccountDeletionEventConsumer[F[_]] = EventConsumer[F, AccountDeletionEvent.Scheduled]

/*
 * Deals with an account's permanent deletion scheduling:
 *
 * - `ToSchedule`: not yet scheduled
 * - `Scheduled`: the deletion has been scheduled with the specific timestamp.
 */
object AccountDeletionEvent:
  case class ToSchedule(userId: UserId, permanentDeletionAt: Instant)
  type Scheduled = ToSchedule & WithUuid[EventId]

  def scheduled(eventId: EventId, userId: UserId, permanentDeletionAt: Instant): AccountDeletionEvent.Scheduled =
    new ToSchedule(userId, permanentDeletionAt) with WithUuid[EventId]:
      override val uuid: EventId = eventId
