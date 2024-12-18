package org.fiume.sketch.shared.auth.accounts.jobs

import org.fiume.sketch.shared.auth.UserId
import org.fiume.sketch.shared.common.WithUuid
import org.fiume.sketch.shared.common.jobs.{EventConsumer, EventProducer, JobId}

import java.time.Instant

type AccountDeletionEventProducer[F[_]] =
  EventProducer[F, AccountDeletionEvent.Unscheduled, AccountDeletionEvent.Scheduled, UserId]
type AccountDeletionEventConsumer[F[_]] = EventConsumer[F, AccountDeletionEvent.Scheduled]

enum AccountDeletionEvent:
  case Unscheduled(userId: UserId, permanentDeletionAt: Instant)
  case Scheduled(uuid: JobId, userId: UserId, permanentDeletionAt: Instant) extends AccountDeletionEvent with WithUuid[JobId]

object AccountDeletionEvent:
  def scheduled(uuid: JobId, userId: UserId, permanentDeletionAt: Instant): AccountDeletionEvent.Scheduled =
    AccountDeletionEvent.Scheduled(uuid, userId, permanentDeletionAt)
