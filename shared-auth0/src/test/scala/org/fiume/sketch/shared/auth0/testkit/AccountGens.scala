package org.fiume.sketch.shared.auth0.testkit

import org.fiume.sketch.shared.auth0.domain.{Account, AccountState}
import org.fiume.sketch.shared.auth0.domain.AccountState.*
import org.fiume.sketch.shared.auth0.testkit.UserGens.*
import org.scalacheck.{Arbitrary, Gen}

import java.time.Instant

object AccountGens:

  given Arbitrary[Account] = Arbitrary(accounts)
  def accounts: Gen[Account] = for
    uuid <- userIds
    credentials <- credentials
    state <- accountStates
  yield Account(uuid, credentials, state)

  given Arbitrary[AccountState] = Arbitrary(accountStates)
  def accountStates: Gen[AccountState] = Gen.oneOf(
    Gen.const(Active(Instant.now())),
    Gen.const(SoftDeleted(Instant.now()))
  )
