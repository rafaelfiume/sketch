package org.fiume.sketch.shared.auth0

import cats.{Eq, Show}
import cats.data.{EitherNec, Validated}
import cats.implicits.*
import org.fiume.sketch.shared.auth0.Model.Username.InvalidUsername
import org.fiume.sketch.shared.auth0.Passwords.{HashedPassword, Salt}

import java.util.UUID

object Model: // TODO Rename it to Users
  case class User(
    uuid: UUID,
    username: Username
  )

  // set of information required to authenticate a user
  case class Credentials(
    uuid: UUID,
    username: Username,
    hashedPassword: HashedPassword
    // salt: Salt
  )

  sealed abstract case class Username(value: String)

  object Username:
    sealed trait InvalidUsername:
      def message: String

    case class TooShort(minLength: Int) extends InvalidUsername:
      override val message: String = s"must be at least $minLength characters long"

    case class TooLong(maxLength: Int) extends InvalidUsername:
      override val message: String = s"must be at most $maxLength characters long"

    case object InvalidChar extends InvalidUsername:
      override val message: String = "must only contain letters (a-z, A-Z), numbers (0-9), and underscores (_)"

    case object ReservedWords extends InvalidUsername:
      override val message: String = "must not contain reserved words"

    case object ExcessiveRepeatedChars extends InvalidUsername:
      override val message: String = "must not contain excessive repeated characters"

    val minLength = 3
    val maxLength = 40
    val reservedWords = Set("admin", "administrator", "root", "superuser", "super", "su", "sudo", "god", "moderator", "mod")
    val maxRepeatedCharsPercentage = 0.7f

    /* must be used during user sign up */
    def validated(value: String): EitherNec[InvalidUsername, Username] =
      val hasMinLength = Validated.condNec[InvalidUsername, Unit](value.length >= minLength, (), TooShort(value.length))
      val hasMaxLength = Validated.condNec(value.length <= maxLength, (), TooLong(value.length))
      val hasNoInvalidChar = Validated.condNec("^[a-zA-Z0-9_-]+$".r.matches(value), (), InvalidChar)
      val hasNoReservedWords = Validated.condNec(!reservedWords.exists(value.contains(_)), (), ReservedWords)
      val hasNoExcessiveRepeatedChars = Validated.condNec(!hasExcessiveRepeatedChars(value, 0.7), (), ExcessiveRepeatedChars)
      (hasMinLength, hasMaxLength, hasNoInvalidChar, hasNoReservedWords, hasNoExcessiveRepeatedChars)
        .mapN((_, _, _, _, _) => notValidatedFromString(value))
        .toEither

    /* safe to be used except during user sign up */
    def notValidatedFromString(value: String): Username = new Username(value) {}

    private def hasExcessiveRepeatedChars(value: String, maxRepeatedCharsPercentage: Float): Boolean =
      val repeatedCharsCount = value.groupBy(identity).view.mapValues(_.length)
      val maxRepeatedCharsCount = value.length * maxRepeatedCharsPercentage
      repeatedCharsCount.values.exists(_ >= maxRepeatedCharsCount)

  given Show[UUID] = Show.fromToString
  given Show[Username] = Show.fromToString
  given Show[User] = Show.fromToString
  given Eq[InvalidUsername] = Eq.fromUniversalEquals[InvalidUsername]