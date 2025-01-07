package org.fiume.sketch.auth.config

import org.fiume.sketch.shared.common.config.DynamicConfig
import org.fiume.sketch.shared.common.events.Recipient

object Dynamic:
  case object RecipientsKey extends DynamicConfig.Key[Set[Recipient]]
