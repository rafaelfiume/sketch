package org.fiume.sketch.shared.auth0.test

import org.fiume.sketch.shared.auth0.Model.{User, Username}
import org.fiume.sketch.shared.test.Gens
import org.scalacheck.{Arbitrary, Gen}

trait UserGens:

  given Arbitrary[Username] = Arbitrary(usernames)
  def usernames: Gen[Username] = Gens.Strings.alphaNumString(1, 60).map(Username(_))

  given Arbitrary[User] = Arbitrary(users)
  def users: Gen[User] =
    for
      uuid <- Gen.uuid
      username <- usernames
    yield User(uuid, username)
