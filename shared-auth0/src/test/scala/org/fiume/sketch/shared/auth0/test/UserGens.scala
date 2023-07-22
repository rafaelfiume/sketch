package org.fiume.sketch.shared.auth0.test

import org.fiume.sketch.shared.auth0.Passwords.{HashedPassword, PlainPassword, Salt}
import org.fiume.sketch.shared.auth0.User
import org.fiume.sketch.shared.auth0.User.{Credentials, Username}
import org.fiume.sketch.shared.auth0.test.PasswordsGens.*
import org.fiume.sketch.shared.auth0.test.PasswordsGens.HashedPasswords.*
import org.fiume.sketch.shared.auth0.test.UserGens.Usernames.*
import org.scalacheck.{Arbitrary, Gen}

import java.util.UUID
import scala.util.Random

object UserGens:

  object Usernames:
    given Arbitrary[Username] = Arbitrary(usernames)
    def usernames: Gen[Username] = Gen
      .choose(Username.minLength, Username.maxLength)
      .flatMap { usernamesWithSize(_) }

    def usernamesWithSize(size: Int): Gen[Username] = Gen
      .listOfN(size, usernameChars)
      .map(_.mkString)
      .map(Username.notValidatedFromString)

    def shortUsernames: Gen[String] =
      Gen
        .choose(0, Username.minLength - 1)
        .flatMap { usernamesWithSize(_) }
        .map(_.value) :| "short usernames"

    def longUsernames: Gen[String] =
      Gen
        .choose(Username.maxLength, 100)
        .flatMap { usernamesWithSize(_) }
        .suchThat(_.value.length > Username.maxLength)
        .map(_.value) :| "long usernames"

    def usernamesWithInvalidChars: Gen[String] =
      (for
        usernames <- usernames
        invalidChars <- Gen
          .nonEmptyListOf(
            Gen.oneOf(" ", "\t", "\n", "\r", "\f", "*", "(", ")", "[", "]", "{", "}", "|", "\\", "'", "\"", "<", ">", "/")
          )
          .map(_.mkString)
      yield Random.shuffle(usernames.value ++ invalidChars).mkString) :| "invalid chars"

    def usernamesWithReservedWords: Gen[String] =
      (for
        usernames <- usernames
        reservedWord <- Gen.oneOf(Username.reservedWords)
        reservedUsername = usernames.value ++ reservedWord.mkString
      yield reservedUsername) :| "reserved words"

    def usernamesWithRepeatedChars: Gen[String] =
      (for
        atMostSize <- Gen.oneOf(Username.minLength, Username.maxLength)
        username <- usernamesWithSize(atMostSize).map(_.value)
        repeatedChar <- usernameChars
        repeatedCharLength = math.ceil(username.length * Username.maxRepeatedCharsPercentage).toInt
        repeatedCharString = repeatedChar.toString * repeatedCharLength
        repeatedUsername = username.dropRight(repeatedCharLength) ++ repeatedCharString
      yield repeatedUsername) :| "repeated chars"

    // frequency is important here avoiding user names like "___", as we want to generate more valid passwords than invalid ones.
    def usernameChars: Gen[Char] = Gen.frequency(9 -> Gen.alphaNumChar, 1 -> Gen.const('_'), 1 -> Gen.const('-'))

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
