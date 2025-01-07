package org.fiume.sketch.shared.auth.accounts

import org.fiume.sketch.shared.auth.UserId
import org.fiume.sketch.shared.common.WithUuid
import org.fiume.sketch.shared.common.events.{EventConsumer, EventId, EventProducer, Recipient}

type AccountDeletedNotificationProducer[F[_]] =
  EventProducer[F, AccountDeletedNotification.ToNotify]

type AccountDeletedNotificationConsumer[F[_]] = EventConsumer[F, AccountDeletedNotification.Notified]

/*
 * Informs other system that an account permanent deletion has occurred:
 *
 * `ToNotify`: a ready-to-be-sent notification about a deletion
 * `Notified`: a notification about a deletion that has been sent to the specified recipient.
 */
object AccountDeletedNotification:
  case class ToNotify(userId: UserId, recipient: Recipient)
  type Notified = ToNotify & WithUuid[EventId]

  def notified(eventId: EventId, userId: UserId, recipient: Recipient): Notified =
    new ToNotify(userId, recipient) with WithUuid[EventId]:
      val uuid: EventId = eventId
