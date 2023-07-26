package org.fiume.sketch.shared.auth0

import cats.{Eq, Show}
import cats.data.{EitherNec, Validated}
import cats.implicits.*
import org.fiume.sketch.shared.app.WithUuid
import org.fiume.sketch.shared.app.troubleshooting.{InvariantError, InvariantHolder}
import org.fiume.sketch.shared.auth0.Passwords.{HashedPassword, Salt}
import org.fiume.sketch.shared.auth0.User.Username
import org.fiume.sketch.shared.auth0.User.Username.WeakUsername

import java.util.UUID

case class User(uuid: UUID, username: Username) extends WithUuid

object User:
  type UserCredentialsWithId = UserCredentials with WithUuid

  // set of information required to authenticate a user
  case class UserCredentials(
    username: Username,
    hashedPassword: HashedPassword,
    salt: Salt
  )

  object UserCredentials:
    def withUuid(uuid0: UUID, username: Username, hashedPassword: HashedPassword, salt: Salt): UserCredentialsWithId =
      new UserCredentials(username, hashedPassword, salt) with WithUuid:
        override val uuid: UUID = uuid0

  sealed abstract case class Username(value: String)

  object Username extends InvariantHolder[WeakUsername]:
    sealed trait WeakUsername extends InvariantError

    case object TooShort extends WeakUsername:
      override def uniqueCode: String = "username.too.short"
      override val message: String = s"must be at least $minLength characters long"

    case object TooLong extends WeakUsername:
      override def uniqueCode: String = "username.too.long"
      override val message: String = s"must be at most $maxLength characters long"

    case object InvalidChar extends WeakUsername:
      override def uniqueCode: String = "username.invalid.characters"
      override val message: String = "must only contain letters (a-z, A-Z), numbers (0-9), and underscores (_)"

    case object ReservedWords extends WeakUsername:
      override def uniqueCode: String = "username.reserved.words"
      override val message: String = "must not contain reserved words"

    case object ExcessiveRepeatedChars extends WeakUsername:
      override def uniqueCode: String = "username.excessive.repeated.characters"
      override val message: String = "must not contain excessive repeated characters"

    val minLength = 8
    val maxLength = 40
    val reservedWords = Set("administrator", "superuser", "moderator")
    val maxRepeatedCharsPercentage = 0.7f
    override val invariantErrors = Set(TooShort, TooLong, InvalidChar, ReservedWords, ExcessiveRepeatedChars)

    /* must be used during user sign up */
    def validated(value: String): EitherNec[WeakUsername, Username] =
      val hasMinLength = Validated.condNec[WeakUsername, Unit](value.length >= minLength, (), TooShort)
      val hasMaxLength = Validated.condNec(value.length <= maxLength, (), TooLong)
      val hasNoInvalidChar = Validated.condNec("^[a-zA-Z0-9_-]+$".r.matches(value), (), InvalidChar)
      val hasNoReservedWords = Validated.condNec(!reservedWords.exists(value.contains(_)), (), ReservedWords)
      val hasNoExcessiveRepeatedChars = Validated.condNec(!hasExcessiveRepeatedChars(value, 0.7), (), ExcessiveRepeatedChars)
      (hasMinLength, hasMaxLength, hasNoInvalidChar, hasNoReservedWords, hasNoExcessiveRepeatedChars)
        .mapN((_, _, _, _, _) => notValidatedFromString(value))
        .toEither

    def notValidatedFromString(value: String): Username = new Username(value) {}

    private def hasExcessiveRepeatedChars(value: String, maxRepeatedCharsPercentage: Float): Boolean =
      val repeatedCharsCount = value.groupBy(identity).view.mapValues(_.length)
      val maxRepeatedCharsCount = value.length * maxRepeatedCharsPercentage
      repeatedCharsCount.values.exists(_ >= maxRepeatedCharsCount)

  given Show[UUID] = Show.fromToString
  given Show[Username] = Show.fromToString
  given Show[User] = Show.fromToString
  given Eq[WeakUsername] = Eq.fromUniversalEquals[WeakUsername]
