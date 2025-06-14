package org.fiume.sketch.shared.common.http.json

import io.circe.Json
import org.http4s.{Charset, EntityEncoder, MediaType}
import org.http4s.headers.`Content-Type`

import NewlineDelimitedJson.Line
import NewlineDelimitedJson.Linebreak

/*
 * Experimental newline delimited json.
 *
 * Consult https://jsonlines.org/ and see
 * `GET /documents` in DocumentsRoutes.scala for example usage.
 */
enum NewlineDelimitedJson:
  case Line(json: Json) extends NewlineDelimitedJson
  case Linebreak extends NewlineDelimitedJson

object NewlineDelimitedJsonEncoder:
  def make[F[_]]: EntityEncoder[F, NewlineDelimitedJson] =
    EntityEncoder.stringEncoder
      .contramap[NewlineDelimitedJson] { line =>
        line match
          case Line(value) => value.noSpaces
          case Linebreak   => "\n"
      }
      .withContentType(`Content-Type`(MediaType.application.json, Charset.`UTF-8`))
