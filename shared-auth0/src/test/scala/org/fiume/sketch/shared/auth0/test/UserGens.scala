package org.fiume.sketch.shared.auth0.test

import org.fiume.sketch.shared.auth0.Model.{Credentials, User, Username}
import org.fiume.sketch.shared.auth0.Passwords.{HashedPassword, PlainPassword, Salt}
import org.fiume.sketch.shared.auth0.test.PasswordsGens.*
import org.fiume.sketch.shared.test.Gens
import org.scalacheck.{Arbitrary, Gen}

import java.util.UUID

trait UserGens:

  given Arbitrary[Username] = Arbitrary(usernames)
  def usernames: Gen[Username] = Gens.Strings.alphaNumString(1, 60).map(Username(_))

  given Arbitrary[User] = Arbitrary(users)
  def users: Gen[User] =
    for
      uuid <- Gen.uuid
      username <- usernames
    yield User(uuid, username)

  given Arbitrary[Credentials] = Arbitrary(credentials)
  def credentials: Gen[Credentials] =
    for
      uuid <- Gen.uuid
      username <- usernames
      hashedPassword <- fakeHashedPasswords
    yield Credentials(uuid, username, hashedPassword)

  def usersInfo: Gen[(UUID, Username, PlainPassword, HashedPassword, Salt)] =
    for
      uuid <- Gen.uuid
      username <- usernames
      passwordInfo <- passwordsInfo
    yield (uuid, username, passwordInfo._1, passwordInfo._2, passwordInfo._3)
