package org.fiume.sketch.shared.auth.accounts

import scala.concurrent.duration.{Duration, FiniteDuration}

case class AccountConfig(
  delayUntilPermanentDeletion: Duration,

  /*
   * It should have Moderate Frequency (every minute or hour).
   *
   * A few minutes or even hours of latency won't likely have any impact on the outcome
   * of a user permanent deletion.
   */
  permanentDeletionJobInterval: FiniteDuration
)
