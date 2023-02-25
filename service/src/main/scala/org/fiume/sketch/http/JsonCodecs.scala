package org.fiume.sketch.http

import io.circe.{Decoder, Encoder, HCursor, Json}
import io.circe.Decoder.Result
import org.fiume.sketch.algebras.{Version, Versions}
import org.fiume.sketch.http.Model.AppStatus

object JsonCodecs:
  object AppStatus:
    given Encoder[Model.AppStatus] = new Encoder[Model.AppStatus]:
      override def apply(a: Model.AppStatus): Json =
        Json.obj(
          "healthy" -> Json.fromBoolean(a.healthy),
          "appVersion" -> Json.fromString(a.version.value)
        )

    given Decoder[Model.AppStatus] = new Decoder[Model.AppStatus]:
      override def apply(c: HCursor): Result[Model.AppStatus] =
        for
          healthy <- c.downField("healthy").as[Boolean]
          appVersion <- c.downField("appVersion").as[String]
        yield Model.AppStatus(healthy, Version(appVersion))
