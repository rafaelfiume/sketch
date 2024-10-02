package org.fiume.sketch.shared.http.json

import io.circe.Json
import org.http4s.{Charset, EntityEncoder, MediaType, *}
import org.http4s.headers.`Content-Type`

import NewlineDelimitedJson.Line
import NewlineDelimitedJson.Linebreak

/*
 * Experimental newline delimited json.
 *
 * Consult https://jsonlines.org/ and see
 * `GET /documents` in DocumentsRoutes.scala for example usage.
 */
sealed trait NewlineDelimitedJson
object NewlineDelimitedJson:
  case class Line(json: Json) extends NewlineDelimitedJson
  case object Linebreak extends NewlineDelimitedJson

object NewlineDelimitedJsonEncoder:
  def make[F[_]: cats.Functor]: EntityEncoder[F, NewlineDelimitedJson] =
    EntityEncoder.stringEncoder
      .contramap[NewlineDelimitedJson] { token =>
        token match
          case Line(value) => value.noSpaces
          case Linebreak   => "\n"
      }
      .withContentType(`Content-Type`(MediaType.application.json, Charset.`UTF-8`))
