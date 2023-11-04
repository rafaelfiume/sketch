package org.fiume.sketch.auth0

import cats.{FlatMap, Show}
import cats.effect.Clock
import cats.implicits.*
import io.circe.{Decoder, Encoder, HCursor, Json, ParsingFailure}
import io.circe.parser.parse
import io.circe.syntax.*
import org.fiume.sketch.auth0.JwtError.*
import org.fiume.sketch.shared.auth0.{User, UserUuid}
import org.fiume.sketch.shared.auth0.User.Username
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}
import pdi.jwt.exceptions.*

import java.security.{PrivateKey, PublicKey}
import java.util.UUID
import scala.concurrent.duration.Duration
import scala.util.Try
import scala.util.control.NoStackTrace

sealed abstract case class JwtToken(value: String)

sealed trait JwtError extends Throwable with NoStackTrace:
  def details: String
  override def toString(): String = this.show

object JwtError:
  given Show[JwtError] = Show.show(error => s"${error.getClass.getSimpleName}(${error.details})")

  case class JwtExpirationError(details: String) extends JwtError // Rename to conform to standards, e.g ExpiredToken
  case class JwtEmptySignatureError(details: String) extends JwtError
  case class JwtInvalidTokenError(details: String) extends JwtError
  case class JwtValidationError(details: String) extends JwtError
  case class JwtUnknownError(details: String) extends JwtError

private[auth0] object JwtToken:
  // offset: a shift in time from a reference point
  def makeJwtToken[F[_]: FlatMap: Clock](privateKey: PrivateKey, user: User, expirationOffset: Duration): F[JwtToken] =
    for
      now <- Clock[F].realTimeInstant
      content = Content(
        preferredUsername = user.username
      )
      claim = JwtClaim(
        subject = user.uuid.value.toString.some,
        content = content.asJson.noSpaces,
        issuedAt = now.getEpochSecond.some,
        expiration = now.plusSeconds(expirationOffset.toSeconds).getEpochSecond.some
      )
    yield new JwtToken(value = JwtCirce.encode(claim, privateKey, JwtAlgorithm.ES256)) {}

  def verifyJwtToken(token: JwtToken, publicKey: PublicKey): Either[JwtError, User] =
    (for
      claims <- JwtCirce.decode(token.value, publicKey, Seq(JwtAlgorithm.ES256)).toEither
      uuid <- claims.subject
        .toRight(new RuntimeException("verifyJwtToken: subject is missing"))
        // TODO Extract logic to generate a CustomUuid from String?
        .flatMap(value => Try(UUID.fromString(value)).toEither.map(UserUuid(_)))
      content <- parse(claims.content).flatMap(_.as[Content])
    yield User(uuid, content.preferredUsername)).leftMap(mapJwtErrors)

  def notValidatedFromString(value: String): JwtToken = new JwtToken(value) {}

  private def mapJwtErrors(jwtError: Throwable): JwtError =
    jwtError match
      case e: JwtEmptySignatureException => JwtEmptySignatureError(e.getMessage)
      case e: ParsingFailure             => JwtInvalidTokenError(s"Invalid Jwt token: ${e.getMessage}")
      case e: JwtValidationException     => JwtValidationError(e.getMessage)
      case e: JwtExpirationException     => JwtExpirationError(e.getMessage)
      case e                             => JwtUnknownError(e.getMessage)

  // see https://www.iana.org/assignments/jwt/jwt.xhtml
  private case class Content(preferredUsername: Username)

  private object Content:
    given Encoder[Content] = new Encoder[Content]:
      final def apply(a: Content): Json = Json.obj(
        "preferred_username" -> a.preferredUsername.value.asJson
      )

    given Decoder[Content] = new Decoder[Content]:
      final def apply(c: HCursor): Decoder.Result[Content] =
        c.downField("preferred_username").as[String].map(value => Content(Username.notValidatedFromString(value)))
