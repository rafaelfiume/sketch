package org.fiume.sketch.shared.auth.accounts

import org.fiume.sketch.shared.auth.UserId
import org.fiume.sketch.shared.common.WithUuid
import org.fiume.sketch.shared.common.events.{EventConsumer, EventId, EventProducer}

type AccountDeletedNotificationProducer[F[_]] =
  EventProducer[F, AccountDeletedNotification.ToNotify, AccountDeletedNotification.Notified, UserId]

type AccountDeletedNotificationConsumer[F[_]] = EventConsumer[F, AccountDeletedNotification.Notified]

/*
 * Informs other system that an account permanent deletion has occurred:
 *
 * `ToNotify`: a ready-to-be-sent notification about a deletion
 * `Notified`: a notification about a deletion that has been sent to the specified service.
 */
enum AccountDeletedNotification:
  case ToNotify(userId: UserId, target: Service)
  case Notified(uuid: EventId, userId: UserId, target: Service) extends AccountDeletedNotification with WithUuid[EventId]

// TODO Change Service -> ConsumerGroup?
case class Service(name: String) extends AnyVal
