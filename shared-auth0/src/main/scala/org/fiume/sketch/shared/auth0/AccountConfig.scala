package org.fiume.sketch.shared.auth0

import scala.concurrent.duration.{Duration, FiniteDuration}

case class AccountConfig(
  delayUntilPermanentDeletion: Duration,
  permanentDeletionJobInterval: FiniteDuration
)
