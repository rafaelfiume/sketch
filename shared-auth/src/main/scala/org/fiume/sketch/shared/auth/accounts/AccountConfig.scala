package org.fiume.sketch.shared.auth.accounts

import scala.concurrent.duration.{Duration, FiniteDuration}

case class AccountConfig(
  delayUntilPermanentDeletion: Duration,

  /*
   * User's account permanent deletion should have moderate frequency (every minute or hour).
   */
  permanentDeletionJobInterval: FiniteDuration
)
