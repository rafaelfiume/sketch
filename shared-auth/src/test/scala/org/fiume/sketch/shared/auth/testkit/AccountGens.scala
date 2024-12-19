package org.fiume.sketch.shared.auth.testkit

import org.fiume.sketch.shared.auth.accounts.{Account, AccountState}
import org.fiume.sketch.shared.auth.accounts.AccountState.*
import org.fiume.sketch.shared.auth.testkit.UserGens.*
import org.scalacheck.{Arbitrary, Gen}

import java.time.Instant
import java.time.temporal.ChronoUnit.MILLIS

object AccountGens:

  given Arbitrary[Account] = Arbitrary(accounts)
  def accounts: Gen[Account] = for
    uuid <- userIds
    credentials <- credentials
    state <- accountStates
  yield Account(uuid, credentials, state)

  given Arbitrary[AccountState] = Arbitrary(accountStates)
  def accountStates: Gen[AccountState] = Gen.oneOf(
    Gen.const(Active(Instant.now().truncatedTo(MILLIS))),
    Gen.const(SoftDeleted(Instant.now().truncatedTo(MILLIS)))
  )
