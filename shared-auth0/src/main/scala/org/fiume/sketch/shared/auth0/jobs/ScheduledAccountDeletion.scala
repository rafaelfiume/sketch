package org.fiume.sketch.shared.auth0.jobs

import org.fiume.sketch.shared.auth0.domain.UserId
import org.fiume.sketch.shared.jobs.JobId

import java.time.Instant

case class ScheduledAccountDeletion(
  uuid: JobId,
  userId: UserId,
  permanentDeletionAt: Instant
)
