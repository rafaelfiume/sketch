package org.fiume.sketch.shared.auth

import cats.{Eq, Show}
import cats.data.{EitherNec, Validated}
import cats.implicits.*
import org.fiume.sketch.shared.auth.Passwords.{HashedPassword, Salt}
import org.fiume.sketch.shared.auth.User.Username
import org.fiume.sketch.shared.auth.User.Username.WeakUsernameError
import org.fiume.sketch.shared.auth.User.Username.WeakUsernameError.*
import org.fiume.sketch.shared.common.{Entity, EntityId, InvalidUuid, WithUuid}
import org.fiume.sketch.shared.common.troubleshooting.InvariantError
import org.fiume.sketch.shared.common.typeclasses.FromString

import java.util.UUID

type UserId = EntityId[UserEntity]
object UserId:
  def apply(uuid: UUID): UserId = EntityId[UserEntity](uuid)
  given FromString[InvalidUuid, UserId] = EntityId.FromString.forEntityId(UserId.apply)

sealed trait UserEntity extends Entity

// TODO Review this model
case class User(uuid: UserId, username: Username)

object User:
  type UserCredentialsWithId = UserCredentials & WithUuid[UserId]

  // set of information required to authenticate a user
  case class UserCredentials(
    username: Username,
    hashedPassword: HashedPassword,
    salt: Salt
  )

  object UserCredentials:
    def make(uuid: UserId, credentials: UserCredentials): UserCredentialsWithId =
      make(uuid, credentials.username, credentials.hashedPassword, credentials.salt)

    def make(uuid0: UserId, username: Username, hashedPassword: HashedPassword, salt: Salt): UserCredentialsWithId =
      new UserCredentials(username, hashedPassword, salt) with WithUuid[UserId]:
        override val uuid: UserId = uuid0

  sealed abstract case class Username(value: String)

  object Username:
    enum WeakUsernameError(val key: String, val detail: String) extends InvariantError:
      case TooShort extends WeakUsernameError("username.too.short", s"must be at least $minLength characters long")

      case TooLong extends WeakUsernameError("username.too.long", s"must be at most $maxLength characters long")

      case InvalidChar
          extends WeakUsernameError(
            "username.invalid.character",
            "must only contain letters (a-z, A-Z), numbers (0-9), and a the special characters: (_,-,@,.)"
          )

      case ReservedWords extends WeakUsernameError("username.reserved.words", "must not contain reserved words")

      case ExcessiveRepeatedChars
          extends WeakUsernameError("username.excessive.repeated.characters", "must not contain excessive repeated characters")

    val minLength = 8
    val maxLength = 40
    val reservedWords = Set("administrator", "superuser", "moderator")
    val maxRepeatedCharsPercentage = 0.7f

    /* must be used during user sign up */
    def validated(value: String): EitherNec[WeakUsernameError, Username] =
      val hasMinLength = Validated.condNec[WeakUsernameError, Unit](value.length >= minLength, (), TooShort)
      val hasMaxLength = Validated.condNec(value.length <= maxLength, (), TooLong)
      val hasNoInvalidChar = Validated.condNec("^[a-zA-Z0-9_@.-]+$".r.matches(value), (), InvalidChar)
      val hasNoReservedWords = Validated.condNec(!reservedWords.exists(value.contains(_)), (), ReservedWords)
      val hasNoExcessiveRepeatedChars = Validated.condNec(!hasExcessiveRepeatedChars(value, 0.7), (), ExcessiveRepeatedChars)
      (hasMinLength, hasMaxLength, hasNoInvalidChar, hasNoReservedWords, hasNoExcessiveRepeatedChars)
        .mapN((_, _, _, _, _) => makeUnsafeFromString(value))
        .toEither

    def makeUnsafeFromString(value: String): Username = new Username(value) {}

    private def hasExcessiveRepeatedChars(value: String, maxRepeatedCharsPercentage: Float): Boolean =
      val repeatedCharsCount = value.groupBy(identity).view.mapValues(_.length)
      val maxRepeatedCharsCount = value.length * maxRepeatedCharsPercentage
      repeatedCharsCount.values.exists(_ >= maxRepeatedCharsCount)

  given Show[UUID] = Show.fromToString
  given Show[Username] = Show.fromToString
  given Show[User] = Show.fromToString
  given Eq[WeakUsernameError] = Eq.fromUniversalEquals[WeakUsernameError]
