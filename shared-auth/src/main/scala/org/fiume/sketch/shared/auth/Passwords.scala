package org.fiume.sketch.shared.auth

import cats.{Eq, Show}
import cats.data.{EitherNec, Validated}
import cats.effect.Sync
import cats.implicits.*
import org.fiume.sketch.shared.auth.Passwords.PlainPassword.WeakPasswordError.*
import org.fiume.sketch.shared.common.troubleshooting.InvariantError
import org.mindrot.jbcrypt.BCrypt

object Passwords:

  sealed abstract case class PlainPassword(value: String)

  object PlainPassword:
    enum WeakPasswordError(val key: String, val detail: String) extends InvariantError:
      case TooShort extends WeakPasswordError("password.too.short", s"must be at least $minLength characters long")

      case TooLong extends WeakPasswordError("password.too.long", s"must be at most $maxLength characters long")

      case NoUpperCase extends WeakPasswordError("password.no.uppercase", "must contain at least one uppercase letter")

      case NoLowerCase extends WeakPasswordError("password.no.lowercase", "must contain at least one lowercase letter")

      case NoDigit extends WeakPasswordError("password.no.digit", "must contain at least one digit")

      case NoSpecialChar
          extends WeakPasswordError(
            "password.no.special.character",
            s"must contain at least one special character: ${specialChars.mkString("'", "', '", "'")}"
          )

      case InvalidSpecialChar
          extends WeakPasswordError(
            "password.invalid.special.character",
            s"must not contain any of the following characters: ${invalidSpecialChars.mkString("'", "', '", "'")}"
          )

      case Whitespace extends WeakPasswordError("password.whitespace", "must not contain any whitespace")

      case InvalidChar extends WeakPasswordError("password.invalid.characters", "must not contain control characters or emojis")

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

    def makeUnsafeFromString(value: String): PlainPassword = new PlainPassword(value) {}

    given Show[PlainPassword] = Show.fromToString
    given Eq[WeakPasswordError] = Eq.fromUniversalEquals[WeakPasswordError]

  sealed abstract case class Salt(base64Value: String):
    override def toString(): String = "********"

  object Salt:
    val logRounds = 12

    /* Suspend the effect of being randomly generated */
    def generate[F[_]: Sync](): F[Salt] =
      Sync[F].blocking { BCrypt.gensalt(logRounds) }.map(new Salt(_) {})

    def makeUnsafeFromString(base64Value: String): Salt = new Salt(base64Value) {}

    given Show[Salt] = Show.show(_ => "********")

  sealed abstract case class HashedPassword(base64Value: String):
    override def toString(): String = "********"

  object HashedPassword:
    def hashPassword[F[_]: Sync](password: PlainPassword, salt: Salt): F[HashedPassword] =
      Sync[F].blocking { BCrypt.hashpw(password.value, salt.base64Value) }.map(new HashedPassword(_) {})

    def makeUnsafeFromString(base64Value: String): HashedPassword = new HashedPassword(base64Value) {}

    def verifyPassword[F[_]: Sync](password: PlainPassword, hashedPassword: HashedPassword): F[Boolean] =
      Sync[F].blocking { BCrypt.checkpw(password.value, hashedPassword.base64Value) }

    given Show[HashedPassword] = Show.show(_ => "********")
