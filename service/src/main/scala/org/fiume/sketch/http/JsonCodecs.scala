package org.fiume.sketch.http

import io.circe.{Codec, Decoder, Encoder, HCursor, Json}
import io.circe.Decoder.Result
import io.circe.syntax.*
import org.fiume.sketch.algebras.{HealthCheck, Version, Versions}
import org.fiume.sketch.http.Model.ServiceStatus

// TODO Move to shared lib
object JsonCodecs:
  // TODO Rename
  object ServiceStatus:
    given Encoder[Version] = Encoder.encodeString.contramap(_.value)
    given Decoder[Version] = Decoder.decodeString.map(Version.apply)

    given Encoder[HealthCheck.Infra] = Encoder.encodeString.contramap(_.toString)
    given Decoder[HealthCheck.Infra] = Decoder.decodeString.map(HealthCheck.Infra.valueOf(_))

    given Codec.AsObject[HealthCheck.ServiceHealth] = Codec.codecForEither("Fail", "Ok")

    given Encoder[Model.ServiceStatus] = new Encoder[Model.ServiceStatus]:
      override def apply(a: Model.ServiceStatus): Json =
        Json.obj(
          "version" -> a.version.asJson,
          "health" -> a.health.asJson
        )

    given Decoder[Model.ServiceStatus] = new Decoder[Model.ServiceStatus]:
      override def apply(c: HCursor): Result[Model.ServiceStatus] =
        for
          version <- c.downField("version").as[Version]
          health <- c.downField("health").as[HealthCheck.ServiceHealth]
        yield Model.ServiceStatus(version, health)
