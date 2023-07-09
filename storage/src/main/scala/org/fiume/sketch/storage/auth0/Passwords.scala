package org.fiume.sketch.storage.auth0

import cats.{Eq, Show}
import cats.data.{EitherNec, Validated}
import cats.effect.Sync
import cats.implicits.*
import org.mindrot.jbcrypt.BCrypt

import java.util.Base64

object Passwords:

  sealed abstract case class PlainPassword(value: String)

  object PlainPassword:
    sealed trait WeakPassword:
      def message: String

    case class TooShort(minLength: Int) extends WeakPassword:
      override val message: String = s"must be at least $minLength characters long"

    case class TooLong(maxLength: Int) extends WeakPassword:
      override val message: String = s"must be at most $maxLength characters long"

    case object NoUpperCase extends WeakPassword:
      override val message: String = "must contain at least one uppercase letter"

    case object NoLowerCase extends WeakPassword:
      override val message: String = "must contain at least one lowercase letter"

    case object NoDigit extends WeakPassword:
      override val message: String = "must contain at least one digit"

    case object NoSpecialChar extends WeakPassword:
      override val message: String = s"must contain at least one special character: ${specialChars.mkString("'", "', '", "'")}"

    case object InvalidSpecialChar extends WeakPassword:
      override val message: String =
        s"must not contain any of the following characters: ${invalidSpecialChars.mkString("'", "', '", "'")}"

    case object Whitespace extends WeakPassword:
      override val message: String = "must not contain any whitespace"

    case object InvalidCharater extends WeakPassword:
      override def message: String =
        s"must not contain control characters or emojis"

    val minLength = 12
    val maxLength = 64
    val specialChars = Set('!', '@', '#', '$', '%', '^', '&', '*', '_', '+', '=', '~', ';', ':', ',', '.', '?')
    val invalidSpecialChars = Set('(', ')', '[', ']', '{', '}', '|', '\\', '\'', '"', '<', '>', '/')

    def validated(value: String): EitherNec[WeakPassword, PlainPassword] =
      val hasMinLength = Validated.condNec[WeakPassword, Unit](value.length >= minLength, (), TooShort(value.length))
      val hasMaxLength = Validated.condNec(value.length <= maxLength, (), TooLong(value.length))
      val hasUppercase = Validated.condNec(value.exists(_.isUpper), (), NoUpperCase)
      val hasLowercase = Validated.condNec(value.exists(_.isLower), (), NoLowerCase)
      val hasDigit = Validated.condNec(value.exists(_.isDigit), (), NoDigit)
      val hasSpecialChar = Validated.condNec(value.exists(specialChars.contains), (), NoSpecialChar)
      val hasNoInvalidSpecialChar = Validated.condNec(value.forall(!invalidSpecialChars.contains(_)), (), InvalidSpecialChar)
      val hasNoWhitespace = Validated.condNec(value.forall(!_.isWhitespace), (), Whitespace)
      val hasNoUnexpectedChar = Validated.condNec(
        value.forall(c => c.isUpper || c.isLower || c.isDigit || specialChars.contains(c)),
        (),
        InvalidCharater
      )

      (hasMinLength,
       hasMaxLength,
       hasUppercase,
       hasLowercase,
       hasDigit,
       hasSpecialChar,
       hasNoInvalidSpecialChar,
       hasNoWhitespace,
       hasNoUnexpectedChar
      ).mapN { case (_, _, _, _, _, _, _, _, _) =>
        PlainPassword.unsafeFromString(value)
      }.toEither

    def unsafeFromString(value: String): PlainPassword = new PlainPassword(value) {}

    given Show[PlainPassword] = Show.fromToString
    given Eq[WeakPassword] = Eq.fromUniversalEquals[WeakPassword]

  sealed abstract case class Salt(base64Value: String)

  object Salt:
    val logRounds = 12

    def generate[F[_]]()(using F: Sync[F]): F[Salt] = F.delay { BCrypt.gensalt(logRounds) }.map(Salt.unsafeFromString)

    def unsafeFromString(base64Value: String): Salt = new Salt(base64Value) {}

    given Show[Salt] = Show.fromToString

  sealed abstract case class HashedPassword(base64Value: String):
    override def toString(): String = "********"

  object HashedPassword:
    def hashPassword(password: PlainPassword, salt: Salt): HashedPassword =
      val hashedPassword = BCrypt.hashpw(password.value, salt.base64Value)
      HashedPassword.unsafeFromString(hashedPassword)

    def unsafeFromString(base64Value: String): HashedPassword = new HashedPassword(base64Value) {}

    def verifyPassword(password: PlainPassword, hashedPassword: HashedPassword): Boolean =
      BCrypt.checkpw(password.value, hashedPassword.base64Value)

    given Show[HashedPassword] = Show.show(_ => "********")
