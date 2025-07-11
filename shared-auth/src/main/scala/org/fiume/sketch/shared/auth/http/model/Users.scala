package org.fiume.sketch.shared.auth.http.model

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import org.fiume.sketch.shared.auth.UserId
import org.fiume.sketch.shared.auth.UserId.given
import org.fiume.sketch.shared.auth.accounts.AccountDeletionEvent
import org.fiume.sketch.shared.common.events.EventId

import java.time.Instant

object Users:

  case class ScheduledForPermanentDeletionResponse(eventId: EventId, userId: UserId, permanentDeletionAt: Instant)

  extension (job: AccountDeletionEvent.Scheduled)
    def asResponsePayload: ScheduledForPermanentDeletionResponse =
      ScheduledForPermanentDeletionResponse(job.uuid, job.userId, job.permanentDeletionAt)

  object UserIdVar:
    def unapply(uuid: String): Option[UserId] = uuid.parsed().toOption

  object json:
    given Encoder[EventId] = Encoder.encodeUUID.contramap[EventId](_.value)
    given Decoder[EventId] = Decoder.decodeUUID.map(EventId(_))

    given Encoder[UserId] = Encoder.encodeUUID.contramap[UserId](_.value)
    given Decoder[UserId] = Decoder.decodeUUID.map(UserId(_))

    given Encoder[ScheduledForPermanentDeletionResponse] = deriveEncoder
    given Decoder[ScheduledForPermanentDeletionResponse] = deriveDecoder
