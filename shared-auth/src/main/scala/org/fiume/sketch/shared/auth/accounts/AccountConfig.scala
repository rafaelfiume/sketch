package org.fiume.sketch.shared.auth.accounts

import scala.concurrent.duration.{Duration, FiniteDuration}

case class AccountConfig(
  delayUntilPermanentDeletion: Duration,
  permanentDeletionJobInterval: FiniteDuration
)
