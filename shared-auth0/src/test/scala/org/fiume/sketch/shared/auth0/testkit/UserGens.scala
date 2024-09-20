package org.fiume.sketch.shared.auth0.testkit

import org.fiume.sketch.shared.auth0.domain.{User, UserId}
import org.fiume.sketch.shared.auth0.domain.Passwords.{HashedPassword, PlainPassword}
import org.fiume.sketch.shared.auth0.domain.User.{UserCredentials, UserCredentialsWithId, Username}
import org.fiume.sketch.shared.auth0.testkit.PasswordsGens.*
import org.scalacheck.{Arbitrary, Gen}

import scala.util.Random

object UserGens:

  given Arbitrary[UserId] = Arbitrary(userIds)
  def userIds: Gen[UserId] = Gen.uuid.map(UserId(_)) :| "UserId"

  given Arbitrary[Username] = Arbitrary(validUsernames)
  def validUsernames: Gen[Username] =
    usernames.map(Username.makeUnsafeFromString)
      :| "usernames"

  def usernames: Gen[String] = Gen
    .choose(Username.minLength, Username.maxLength)
    .flatMap { usernamesWithSize(_) }
    :| "username strings"

  def usernamesWithSize(size: Int): Gen[String] = Gen
    .listOfN(size, usernameChars)
    .map(_.mkString)
    :| "username strings"

  def shortUsernames: Gen[String] =
    Gen
      .choose(0, Username.minLength - 1)
      .flatMap { usernamesWithSize(_) }
      :| "short usernames"

  def longUsernames: Gen[String] =
    Gen
      .choose(Username.maxLength, 100)
      .flatMap { usernamesWithSize(_) }
      .suchThat(_.length > Username.maxLength)
      :| "long usernames"

  def usernamesWithInvalidChars: Gen[String] =
    (for
      usernames <- usernames
      invalidChars <- Gen
        .nonEmptyListOf(
          Gen.oneOf(" ", "\t", "\n", "\r", "\f", "*", "(", ")", "[", "]", "{", "}", "|", "\\", "'", "\"", "<", ">", "/")
        )
        .map(_.mkString)
    yield Random.shuffle(usernames ++ invalidChars).mkString) :| "invalid chars"

  def usernamesWithReservedWords: Gen[String] =
    (for
      usernames <- usernames
      reservedWord <- Gen.oneOf(Username.reservedWords)
      reservedUsername = usernames ++ reservedWord.mkString
    yield reservedUsername) :| "reserved words"

  def usernamesWithRepeatedChars: Gen[String] =
    (for
      atMostSize <- Gen.oneOf(Username.minLength, Username.maxLength)
      username <- usernamesWithSize(atMostSize)
      repeatedChar <- usernameChars
      repeatedCharLength = math.ceil(username.length * Username.maxRepeatedCharsPercentage).toInt
      repeatedCharString = repeatedChar.toString * repeatedCharLength
      repeatedUsername = username.dropRight(repeatedCharLength) ++ repeatedCharString
    yield repeatedUsername) :| "repeated chars"

  def oneOfUsernameInputErrors: Gen[String] = Gen.oneOf(
    shortUsernames,
    longUsernames,
    usernamesWithInvalidChars,
    usernamesWithReservedWords,
    usernamesWithRepeatedChars
  ) :| "one of username input errors"

  given Arbitrary[User] = Arbitrary(users)
  def users: Gen[User] =
    for
      uuid <- userIds
      username <- validUsernames
    yield User(uuid, username)

  given Arbitrary[UserCredentials] = Arbitrary(credentials)
  def credentials: Gen[UserCredentials] =
    for
      username <- validUsernames
      hashedPassword <- fakeHashedPasswords
      salt <- salts
    yield UserCredentials(username, hashedPassword, salt)

  import cats.effect.IO
  given cats.effect.unsafe.IORuntime = cats.effect.unsafe.IORuntime.global
  def validCredentialsWithIdAndPlainPassword: Gen[(UserCredentialsWithId, PlainPassword)] =
    for
      uuid <- userIds
      username <- validUsernames
      plainPassword <- validPlainPasswords
      salt <- salts
      hashedPassword = HashedPassword.hashPassword[IO](plainPassword, salt).unsafeRunSync()
    yield UserCredentials.make(uuid, username, hashedPassword, salt) -> plainPassword

  // frequency is important here avoiding user names like "___", as we want to generate more valid passwords than invalid ones.
  private def usernameChars: Gen[Char] = Gen.frequency(
    9 -> Gen.alphaNumChar,
    2 -> Gen.const('@'),
    2 -> Gen.const('.'),
    1 -> Gen.const('_'),
    1 -> Gen.const('-')
  )
