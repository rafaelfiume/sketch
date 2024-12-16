package org.fiume.sketch.shared.auth.http.model

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import org.fiume.sketch.shared.auth.UserId
import org.fiume.sketch.shared.auth.UserId.given
import org.fiume.sketch.shared.auth.accounts.jobs.ScheduledAccountDeletion
import org.fiume.sketch.shared.common.jobs.JobId
import org.fiume.sketch.shared.common.troubleshooting.syntax.ErrorInfoSyntax.*

import java.time.Instant

object Users:

  case class ScheduledForPermanentDeletionResponse(jobId: JobId, userId: UserId, permanentDeletionAt: Instant)

  extension (job: ScheduledAccountDeletion)
    def asResponsePayload: ScheduledForPermanentDeletionResponse =
      ScheduledForPermanentDeletionResponse(job.uuid, job.userId, job.permanentDeletionAt)

  object UserIdVar:
    def unapply(uuid: String): Option[UserId] = uuid.parsed().toOption

  object json:
    given Encoder[JobId] = Encoder.encodeUUID.contramap[JobId](_.value)
    given Decoder[JobId] = Decoder.decodeUUID.map(JobId(_))

    given Encoder[UserId] = Encoder.encodeUUID.contramap[UserId](_.value)
    given Decoder[UserId] = Decoder.decodeUUID.map(UserId(_))

    given Encoder[ScheduledForPermanentDeletionResponse] = deriveEncoder
    given Decoder[ScheduledForPermanentDeletionResponse] = deriveDecoder
