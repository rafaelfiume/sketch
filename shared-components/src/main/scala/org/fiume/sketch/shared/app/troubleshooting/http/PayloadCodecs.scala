package org.fiume.sketch.shared.app.troubleshooting.http

import io.circe.{Codec, Decoder, Encoder, HCursor, Json}
import io.circe.Decoder.Result
import io.circe.syntax.*
import org.fiume.sketch.shared.app.algebras.HealthCheck.ServiceHealth
import org.fiume.sketch.shared.app.algebras.Versions
import org.fiume.sketch.shared.app.algebras.Versions.Version
import org.fiume.sketch.shared.app.troubleshooting.{ErrorDetails, ErrorInfo, ErrorMessage, ServiceStatus}

object PayloadCodecs:
  object ErrorInfoCodecs:
    given Encoder[ErrorMessage] = Encoder.encodeString.contramap(_.value)
    given Decoder[ErrorMessage] = Decoder.decodeString.map(ErrorMessage.apply)

    given Encoder[ErrorDetails] = Encoder.encodeMap[String, String].contramap(_.tips)
    given Decoder[ErrorDetails] = Decoder.decodeMap[String, String].map(ErrorDetails.apply)

    given Encoder[ErrorInfo] = new Encoder[ErrorInfo]:
      override def apply(errorInfo: ErrorInfo): Json =
        Json.obj(
          "message" -> errorInfo.message.asJson,
          "details" -> errorInfo.details.asJson
        )

    given Decoder[ErrorInfo] = new Decoder[ErrorInfo]:
      override def apply(c: HCursor): Result[ErrorInfo] =
        for
          message <- c.downField("message").as[ErrorMessage]
          details <- c.downField("details").as[Option[ErrorDetails]]
        yield ErrorInfo(message, details)

  object ServiceStatusCodecs:
    given Encoder[Versions.Environment] = Encoder.encodeString.contramap(_.name)
    given Decoder[Versions.Environment] = Decoder.decodeString.map(Versions.Environment.apply)
    given Encoder[ServiceHealth.Infra] = Encoder.encodeString.contramap(_.toString)
    given Decoder[ServiceHealth.Infra] = Decoder.decodeString.map(ServiceHealth.Infra.valueOf(_))
    given Codec.AsObject[ServiceHealth] = Codec.codecForEither("Fail", "Ok")

    given Encoder[ServiceStatus] = new Encoder[ServiceStatus]:
      override def apply(service: ServiceStatus): Json =
        Json.obj(
          "env" -> service.version.env.asJson,
          "build" -> service.version.build.asJson,
          "commit" -> service.version.commit.asJson,
          "health" -> service.health.asJson
        )

    given Decoder[ServiceStatus] = new Decoder[ServiceStatus]:
      override def apply(c: HCursor): Result[ServiceStatus] =
        for
          env <- c.downField("env").as[Versions.Environment]
          build <- c.downField("build").as[String]
          commit <- c.downField("commit").as[String]
          health <- c.downField("health").as[ServiceHealth]
        yield ServiceStatus(Version(env, build, commit), health)
