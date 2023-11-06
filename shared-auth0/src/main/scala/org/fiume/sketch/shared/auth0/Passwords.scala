package org.fiume.sketch.shared.auth0

import cats.{Eq, Show}
import cats.data.{EitherNec, Validated}
import cats.effect.Sync
import cats.implicits.*
import org.fiume.sketch.shared.app.troubleshooting.InvariantError
import org.fiume.sketch.shared.auth0.Passwords.PlainPassword.WeakPasswordError
import org.fiume.sketch.shared.auth0.Passwords.PlainPassword.WeakPasswordError.*
import org.mindrot.jbcrypt.BCrypt

object Passwords:

  sealed abstract case class PlainPassword(value: String)

  object PlainPassword:
    sealed trait WeakPasswordError extends InvariantError

    object WeakPasswordError:
      case object TooShort extends WeakPasswordError:
        override val uniqueCode: String = "password.too.short"
        override val message: String = s"must be at least $minLength characters long"

      case object TooLong extends WeakPasswordError:
        override val uniqueCode: String = "password.too.long"
        override val message: String = s"must be at most $maxLength characters long"

      case object NoUpperCase extends WeakPasswordError:
        override def uniqueCode: String = "password.no.uppercase"
        override val message: String = "must contain at least one uppercase letter"

      case object NoLowerCase extends WeakPasswordError:
        override def uniqueCode: String = "password.no.lowercase"
        override val message: String = "must contain at least one lowercase letter"

      case object NoDigit extends WeakPasswordError:
        override def uniqueCode: String = "password.no.digit"
        override val message: String = "must contain at least one digit"

      case object NoSpecialChar extends WeakPasswordError:
        override def uniqueCode: String = "password.no.special.character"
        override val message: String = s"must contain at least one special character: ${specialChars.mkString("'", "', '", "'")}"

      case object InvalidSpecialChar extends WeakPasswordError:
        override def uniqueCode: String = "password.invalid.special.character"
        override val message: String =
          s"must not contain any of the following characters: ${invalidSpecialChars.mkString("'", "', '", "'")}"

      case object Whitespace extends WeakPasswordError:
        override def uniqueCode: String = "password.whitespace"
        override val message: String = "must not contain any whitespace"

      case object InvalidChar extends WeakPasswordError:
        override def uniqueCode: String = "password.invalid.characters"
        override def message: String = "must not contain control characters or emojis"

    val minLength = 12
    val maxLength = 64
    val specialChars = Set('!', '@', '#', '$', '%', '^', '&', '*', '_', '+', '=', '~', ';', ':', ',', '.', '?')
    val invalidSpecialChars = Set('(', ')', '[', ']', '{', '}', '|', '\\', '\'', '"', '<', '>', '/')

    def validated(value: String): EitherNec[WeakPasswordError, PlainPassword] =
      val hasMinLength = Validated.condNec[WeakPasswordError, Unit](value.length >= minLength, (), TooShort)
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
        InvalidChar
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

    given Show[PlainPassword] = Show.fromToString
    given Eq[WeakPasswordError] = Eq.fromUniversalEquals[WeakPasswordError]

  sealed abstract case class Salt(base64Value: String)

  object Salt:
    val logRounds = 12

    /* Suspend the effect of being randomly generated */
    def generate[F[_]: Sync](): F[Salt] = Sync[F].blocking { BCrypt.gensalt(logRounds) }.map(new Salt(_) {})

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
