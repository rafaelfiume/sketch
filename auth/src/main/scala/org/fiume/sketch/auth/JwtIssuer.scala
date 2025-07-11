package org.fiume.sketch.auth

import cats.implicits.*
import io.circe.{Decoder, Encoder, HCursor, Json, ParsingFailure}
import io.circe.parser.parse
import io.circe.syntax.*
import org.fiume.sketch.shared.auth.{Jwt, JwtVerificationError, User, UserId}
import org.fiume.sketch.shared.auth.JwtVerificationError.*
import org.fiume.sketch.shared.auth.User.Username
import org.fiume.sketch.shared.auth.UserId.given
import org.fiume.sketch.shared.common.EntityId.given
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}
import pdi.jwt.exceptions.*

import java.security.{PrivateKey, PublicKey}
import java.time.Instant
import scala.concurrent.duration.Duration

private[auth] object JwtIssuer:
  // offset: a shift in time from a reference point
  def make[F[_]](privateKey: PrivateKey, user: User, now: Instant, expirationOffset: Duration): Jwt =
    val content = Content(
      preferredUsername = user.username
    )
    val claim = JwtClaim(
      subject = user.uuid.asString().some,
      content = content.asJson.noSpaces,
      issuedAt = now.getEpochSecond.some,
      expiration = now.plusSeconds(expirationOffset.toSeconds).getEpochSecond.some
    )
    Jwt.makeUnsafeFromString(JwtCirce.encode(claim, privateKey, JwtAlgorithm.ES256))

  def verify(jwt: Jwt, publicKey: PublicKey): Either[JwtVerificationError, User] =
    (for
      claims <- JwtCirce.decode(jwt.value, publicKey, Seq(JwtAlgorithm.ES256)).toEither
      uuid: UserId <- claims.subject
        .toRight(JwtUnknownError("verify: subject is missing"))
        .flatMap { _.parsed().leftMap(e => JwtUnknownError(e.detail)) }
      content <- parse(claims.content).flatMap(_.as[Content])
    yield User(uuid, content.preferredUsername)).leftMap(mapJwtErrors)

  private def mapJwtErrors(jwtError: Throwable | JwtVerificationError): JwtVerificationError =
    jwtError match
      case e: JwtEmptySignatureException => JwtEmptySignatureError(e.getMessage)
      case e: ParsingFailure             => JwtInvalidTokenError(s"Invalid Jwt: ${e.getMessage}")
      case e: JwtValidationException     => JwtValidationError(e.getMessage)
      case e: JwtExpirationException     => JwtExpirationError(e.getMessage)
      case e: JwtVerificationError       => e
      case e: Throwable                  => JwtUnknownError(e.getMessage)

  // see https://www.iana.org/assignments/jwt/jwt.xhtml
  private case class Content(preferredUsername: Username) extends AnyVal

  private object Content:
    given Encoder[Content]:
      override def apply(a: Content): Json = Json.obj(
        "preferred_username" -> a.preferredUsername.value.asJson
      )

    given Decoder[Content]:
      override def apply(c: HCursor): Decoder.Result[Content] =
        c.downField("preferred_username").as[String].map(value => Content(Username.makeUnsafeFromString(value)))
