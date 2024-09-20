package org.fiume.sketch.shared.auth0.jobs

import org.fiume.sketch.shared.auth0.UserId

import java.time.Instant

case class PermanentAccountDeletionJob(
  uuid: JobId,
  userId: UserId,
  permanentDeletionAt: Instant
)
