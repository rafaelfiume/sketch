package org.fiume.sketch.shared.auth.accounts

import org.fiume.sketch.shared.auth.UserId
import org.fiume.sketch.shared.common.WithUuid
import org.fiume.sketch.shared.common.events.{EventConsumer, EventId, EventProducer}

import java.time.Instant

type AccountDeletionEventProducer[F[_]] =
  EventProducer[F, AccountDeletionEvent.ToSchedule, AccountDeletionEvent.Scheduled, UserId]

type AccountDeletionEventConsumer[F[_]] = EventConsumer[F, AccountDeletionEvent.Scheduled]

/*
 * Deals with an account's permanent deletion scheduling:
 *
 * - `ToSchedule`: not yet scheduled
 * - `Scheduled`: the deletion has been scheduled with the specific timestamp.
 */
enum AccountDeletionEvent:
  case ToSchedule(userId: UserId, permanentDeletionAt: Instant)
  case Scheduled(uuid: EventId, userId: UserId, permanentDeletionAt: Instant) extends AccountDeletionEvent with WithUuid[EventId]

object AccountDeletionEvent:
  def scheduled(uuid: EventId, userId: UserId, permanentDeletionAt: Instant): AccountDeletionEvent.Scheduled =
    AccountDeletionEvent.Scheduled(uuid, userId, permanentDeletionAt)
