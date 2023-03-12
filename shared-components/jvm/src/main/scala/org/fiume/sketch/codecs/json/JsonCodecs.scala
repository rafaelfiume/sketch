package org.fiume.sketch.codecs.json

import io.circe.{Codec, Decoder, Encoder, HCursor, Json}
import io.circe.Decoder.Result
import io.circe.syntax.*
import org.fiume.sketch.algebras.{HealthCheck, Version, Versions}
import org.fiume.sketch.app.ServiceStatus
import org.fiume.sketch.domain.documents.Metadata

object Service:
  given Encoder[Version] = Encoder.encodeString.contramap(_.value)
  given Decoder[Version] = Decoder.decodeString.map(Version.apply)

  given Encoder[HealthCheck.Infra] = Encoder.encodeString.contramap(_.toString)
  given Decoder[HealthCheck.Infra] = Decoder.decodeString.map(HealthCheck.Infra.valueOf(_))
  given Codec.AsObject[HealthCheck.ServiceHealth] = Codec.codecForEither("Fail", "Ok")

  given Encoder[ServiceStatus] = new Encoder[ServiceStatus]:
    override def apply(service: ServiceStatus): Json =
      Json.obj(
        "version" -> service.version.asJson,
        "health" -> service.health.asJson
      )

  given Decoder[ServiceStatus] = new Decoder[ServiceStatus]:
    override def apply(c: HCursor): Result[ServiceStatus] =
      for
        version <- c.downField("version").as[Version]
        health <- c.downField("health").as[HealthCheck.ServiceHealth]
      yield ServiceStatus(version, health)
