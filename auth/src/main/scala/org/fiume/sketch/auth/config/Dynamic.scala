package org.fiume.sketch.auth.config

import io.circe.{Decoder, Encoder}
import org.fiume.sketch.shared.common.config.DynamicConfig
import org.fiume.sketch.shared.common.events.Recipient
import org.fiume.sketch.shared.common.typeclasses.AsString

object Dynamic:
  case object RecipientsKey extends DynamicConfig.Key[Set[Recipient]]

  given AsString[RecipientsKey.type]:
    extension (key: RecipientsKey.type) override def asString() = "account.deletion.notification.recipients"

  given Decoder[Recipient] = Decoder.decodeString.map(Recipient(_))
  given Encoder[Recipient] = Encoder.encodeString.contramap(_.name)
