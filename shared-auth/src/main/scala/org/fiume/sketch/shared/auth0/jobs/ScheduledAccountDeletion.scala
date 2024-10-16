package org.fiume.sketch.shared.auth.jobs

import org.fiume.sketch.shared.auth.domain.UserId
import org.fiume.sketch.shared.common.jobs.JobId

import java.time.Instant

case class ScheduledAccountDeletion(
  uuid: JobId,
  userId: UserId,
  permanentDeletionAt: Instant
)
