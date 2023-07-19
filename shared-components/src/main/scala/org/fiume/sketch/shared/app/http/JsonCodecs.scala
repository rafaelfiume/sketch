package org.fiume.sketch.shared.app.http

import io.circe.{Codec, Decoder, Encoder, HCursor, Json}
import io.circe.Decoder.Result
import io.circe.syntax.*
import org.fiume.sketch.shared.app.ServiceStatus
import org.fiume.sketch.shared.app.algebras.HealthCheck.ServiceHealth
import org.fiume.sketch.shared.app.algebras.Versions
import org.fiume.sketch.shared.app.algebras.Versions.Version
import org.fiume.sketch.shared.app.http.Model.{ErrorCode, ErrorDetails, ErrorInfo, ErrorMessage}

import java.time.ZonedDateTime

object JsonCodecs:
  object ServiceStatusCodecs:
    given Encoder[ServiceHealth.Infra] = Encoder.encodeString.contramap(_.toString)
    given Decoder[ServiceHealth.Infra] = Decoder.decodeString.map(ServiceHealth.Infra.valueOf(_))
    given Codec.AsObject[ServiceHealth] = Codec.codecForEither("Fail", "Ok")

    given Encoder[ServiceStatus] = new Encoder[ServiceStatus]:
      override def apply(service: ServiceStatus): Json =
        Json.obj(
          "build" -> service.version.build.asJson,
          "commit" -> service.version.commit.asJson,
          "health" -> service.health.asJson
        )

    given Decoder[ServiceStatus] = new Decoder[ServiceStatus]:
      override def apply(c: HCursor): Result[ServiceStatus] =
        for
          build <- c.downField("build").as[String]
          commit <- c.downField("commit").as[String]
          health <- c.downField("health").as[ServiceHealth]
        yield ServiceStatus(Version(build, commit), health)

  object ErrorInfoCodecs:
    given Encoder[ErrorCode] = Encoder.encodeString.contramap(_.value)
    given Decoder[ErrorCode] = Decoder.decodeString.map(ErrorCode.apply)

    given Encoder[ErrorMessage] = Encoder.encodeString.contramap(_.value)
    given Decoder[ErrorMessage] = Decoder.decodeString.map(ErrorMessage.apply)

    given Encoder[ErrorDetails] = Encoder.encodeString.contramap(_.value)
    given Decoder[ErrorDetails] = Decoder.decodeString.map(ErrorDetails.apply)

    given Encoder[ErrorInfo] = new Encoder[ErrorInfo]:
      override def apply(errorInfo: ErrorInfo): Json =
        Json.obj(
          "code" -> errorInfo.code.asJson,
          "message" -> errorInfo.message.asJson,
          "details" -> errorInfo.details.asJson,
          "timestamp" -> errorInfo.timestamp.asJson
        )

    given Decoder[ErrorInfo] = new Decoder[ErrorInfo]:
      override def apply(c: HCursor): Result[ErrorInfo] =
        for
          code <- c.downField("code").as[ErrorCode]
          message <- c.downField("message").as[ErrorMessage]
          details <- c.downField("details").as[Option[ErrorDetails]]
          timestamp <- c.downField("timestamp").as[Option[ZonedDateTime]]
        yield ErrorInfo(code, message, details, timestamp)
