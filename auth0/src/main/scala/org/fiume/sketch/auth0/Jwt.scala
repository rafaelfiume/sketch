package org.fiume.sketch.auth0

import cats.effect.{Clock, Sync}
import cats.implicits.*
import io.circe.{Decoder, Encoder, HCursor, Json}
import io.circe.parser.parse
import io.circe.syntax.*
import org.fiume.sketch.shared.auth0.Model.{User, Username}
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}

import java.security.{PrivateKey, PublicKey}
import java.time.Instant
import java.util.UUID
import scala.util.{Failure, Success, Try}

case class JwtToken(value: String) extends AnyVal
object JwtToken:
  def createJwtToken[F[_]](privateKey: PrivateKey, user: User)(using F: Sync[F], clock: Clock[F]): F[JwtToken] =
    for
      now <- clock.realTimeInstant
      content = Content.from(user)
      claim = JwtClaim(
        subject = Some(user.uuid.toString),
        content = content.asJson.noSpaces,
        issuedAt = now.getEpochSecond.some,
        expiration = now.plusSeconds(60 * 60).getEpochSecond.some
      )
    yield JwtToken(value = JwtCirce.encode(claim, privateKey, JwtAlgorithm.ES256))

  def verifyJwtToken(token: JwtToken, publicKey: PublicKey): Either[String, User] =
    for
      claims <- (JwtCirce.decode(token.value, publicKey, Seq(JwtAlgorithm.ES256)).toEither.leftMap(_.getMessage))
      uuid <- claims.subject
        .toRight("subject is missing")
        .flatMap(value => Try(UUID.fromString(value)).toEither.leftMap(_.getMessage))
      content <- parse(claims.content).flatMap(_.as[Content]).leftMap(_.getMessage)
    yield User(uuid, content.preferredUsername)

  // see https://www.iana.org/assignments/jwt/jwt.xhtml
  private case class Content(preferredUsername: Username)
  private object Content:
    def from(user: User): Content = Content(preferredUsername = user.username)

    given Encoder[Content] = new Encoder[Content]:
      final def apply(a: Content): Json = Json.obj(
        "preferred_username" -> a.preferredUsername.value.asJson
      )

    given Decoder[Content] = new Decoder[Content]:
      final def apply(c: HCursor): Decoder.Result[Content] =
        c.downField("preferred_username").as[String].map(username => Content(Username(username)))
