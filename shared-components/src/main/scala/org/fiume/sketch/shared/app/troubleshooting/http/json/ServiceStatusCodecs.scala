package org.fiume.sketch.shared.app.troubleshooting.http.json

import io.circe.{Codec, Decoder, Encoder, HCursor, Json}
import io.circe.Decoder.Result
import io.circe.syntax.*
import org.fiume.sketch.shared.app.algebras.HealthCheck.ServiceHealth
import org.fiume.sketch.shared.app.algebras.HealthCheck.ServiceHealth.Infra
import org.fiume.sketch.shared.app.algebras.Versions
import org.fiume.sketch.shared.app.algebras.Versions.*
import org.fiume.sketch.shared.app.troubleshooting.ServiceStatus
import org.fiume.sketch.shared.typeclasses.SemanticStringSyntax.*

object ServiceStatusCodecs:
  given Encoder[Environment] = Encoder.encodeString.contramap(_.name)
  given Decoder[Environment] = Decoder.decodeString.map(Environment.apply)
  given Encoder[Build] = Encoder.encodeString.contramap(_.name)
  given Decoder[Build] = Decoder.decodeString.map(Build.apply)
  given Encoder[Commit] = Encoder.encodeString.contramap(_.name)
  given Decoder[Commit] = Decoder.decodeString.map(Commit.apply)
  given Encoder[Infra] = Encoder.encodeString.contramap(_.asString)
  given Decoder[Infra] = Decoder.decodeString.map(Infra.valueOf(_))
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
        build <- c.downField("build").as[Build]
        commit <- c.downField("commit").as[Commit]
        health <- c.downField("health").as[ServiceHealth]
      yield ServiceStatus(Version(env, build, commit), health)
