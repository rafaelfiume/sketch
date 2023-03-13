package org.fiume.sketch.shared.codecs.json.app

import io.circe.{Codec, Decoder, Encoder, HCursor, Json}
import io.circe.Decoder.Result
import io.circe.syntax.*
import org.fiume.sketch.shared.app.{ServiceHealth, ServiceStatus, Version}
import org.fiume.sketch.shared.app.algebras.Versions
import org.fiume.sketch.shared.domain.documents.Metadata

object Service:
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
