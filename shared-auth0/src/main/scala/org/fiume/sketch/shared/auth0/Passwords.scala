package org.fiume.sketch.shared.auth0

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
      def uniqueCode: String
      def message: String

    case object TooShort extends WeakPassword:
      override val uniqueCode: String = "password.too.short"
      override val message: String = s"must be at least $minLength characters long"

    case object TooLong extends WeakPassword:
      override val uniqueCode: String = "password.too.long"
      override val message: String = s"must be at most $maxLength characters long"

    case object NoUpperCase extends WeakPassword:
      override def uniqueCode: String = "password.no.uppercase"
      override val message: String = "must contain at least one uppercase letter"

    case object NoLowerCase extends WeakPassword:
      override def uniqueCode: String = "password.no.lowercase"
      override val message: String = "must contain at least one lowercase letter"

    case object NoDigit extends WeakPassword:
      override def uniqueCode: String = "password.no.digit"
      override val message: String = "must contain at least one digit"

    case object NoSpecialChar extends WeakPassword:
      override def uniqueCode: String = "password.no.special.character"
      override val message: String = s"must contain at least one special character: ${specialChars.mkString("'", "', '", "'")}"

    case object InvalidSpecialChar extends WeakPassword:
      override def uniqueCode: String = "password.invalid.special.character"
      override val message: String =
        s"must not contain any of the following characters: ${invalidSpecialChars.mkString("'", "', '", "'")}"

    case object Whitespace extends WeakPassword:
      override def uniqueCode: String = "password.whitespace"
      override val message: String = "must not contain any whitespace"

    case object InvalidCharater extends WeakPassword:
      override def uniqueCode: String = "password.invalid.characters"
      override def message: String = "must not contain control characters or emojis"

    val minLength = 12
    val maxLength = 64
    val specialChars = Set('!', '@', '#', '$', '%', '^', '&', '*', '_', '+', '=', '~', ';', ':', ',', '.', '?')
    val invalidSpecialChars = Set('(', ')', '[', ']', '{', '}', '|', '\\', '\'', '"', '<', '>', '/')
    val inputErrors = Set(TooShort, TooLong, NoUpperCase, NoLowerCase, NoDigit, NoSpecialChar, InvalidSpecialChar, Whitespace, InvalidCharater)

    def validated(value: String): EitherNec[WeakPassword, PlainPassword] =
      val hasMinLength = Validated.condNec[WeakPassword, Unit](value.length >= minLength, (), TooShort)
      val hasMaxLength = Validated.condNec(value.length <= maxLength, (), TooLong)
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
        new PlainPassword(value) {}
      }.toEither

    def notValidatedFromString(value: String): PlainPassword = new PlainPassword(value) {}

    def inputErrorsToMap(inputErrors: List[WeakPassword]): Map[String, String] =
      inputErrors.map(e => e.uniqueCode -> e.message).toMap

    given Show[PlainPassword] = Show.fromToString
    given Eq[WeakPassword] = Eq.fromUniversalEquals[WeakPassword]

  sealed abstract case class Salt(base64Value: String)

  object Salt:
    val logRounds = 12

    /* Suspend the effect of being randomly generated */
    def generate[F[_]]()(using F: Sync[F]): F[Salt] = F.delay { BCrypt.gensalt(logRounds) }.map(new Salt(_) {})

    def notValidatedFromString(base64Value: String): Salt = new Salt(base64Value) {}

    given Show[Salt] = Show.fromToString

  sealed abstract case class HashedPassword(base64Value: String):
    override def toString(): String = "********"

  object HashedPassword:
    def hashPassword(password: PlainPassword, salt: Salt): HashedPassword =
      val hashedPassword = BCrypt.hashpw(password.value, salt.base64Value)
      new HashedPassword(hashedPassword) {}

    def notValidatedFromString(base64Value: String): HashedPassword = new HashedPassword(base64Value) {}

    def verifyPassword(password: PlainPassword, hashedPassword: HashedPassword): Boolean =
      BCrypt.checkpw(password.value, hashedPassword.base64Value)

    given Show[HashedPassword] = Show.show(_ => "********")
