package org.fiume.sketch.shared.auth

import scala.concurrent.duration.{Duration, FiniteDuration}

case class AccountConfig(
  delayUntilPermanentDeletion: Duration,
  permanentDeletionJobInterval: FiniteDuration
)
