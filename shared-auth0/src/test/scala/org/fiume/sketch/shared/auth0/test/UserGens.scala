package org.fiume.sketch.shared.auth0.test

import org.fiume.sketch.shared.auth0.Model.{Credentials, User, Username}
import org.fiume.sketch.shared.auth0.Passwords.{HashedPassword, PlainPassword, Salt}
import org.fiume.sketch.shared.auth0.test.PasswordsGens.*
import org.scalacheck.{Arbitrary, Gen}

import java.util.UUID

object UserGens extends UserGens

trait UserGens:

  given Arbitrary[Username] = Arbitrary(usernames)
  def usernames: Gen[Username] = Gen
    .choose(Username.minLength, Username.maxLength)
    .flatMap { usernamesWithSize(_) }

  def usernamesWithSize(size: Int): Gen[Username] = Gen
    .listOfN(size, usernameChars)
    .map(_.mkString)
    .suchThat(Username.validated(_).isRight)
    .map(Username.notValidatedFromString)

  def usernameChars: Gen[Char] = Gen.oneOf(Gen.alphaNumChar, Gen.const('_'), Gen.const('-'))

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

  def usersAuthenticationInfo: Gen[(UUID, Username, PlainPassword, HashedPassword, Salt)] =
    for
      uuid <- Gen.uuid
      username <- usernames
      passwordInfo <- passwordsInfo
    yield (uuid, username, passwordInfo._1, passwordInfo._2, passwordInfo._3)
