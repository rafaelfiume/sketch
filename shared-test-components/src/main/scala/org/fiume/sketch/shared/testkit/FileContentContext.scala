package org.fiume.sketch.shared.testkit

import cats.effect.{Async, Resource, Sync}
import fs2.io.file.{Files, Path}
import fs2.io.file.Files.*
import io.circe.Json
import io.circe.parser.{ parse}
import org.fiume.sketch.shared.testkit.EitherSyntax.*

import scala.io.Source

/*
 * Path is relative to `resource` folders.
 */
trait FileContentContext:
  def jsonFrom[F[_]: Sync](path: String): Resource[F, Json] =
    stringsFrom(path).map(parse(_).rightValue)

  def stringsFrom[F[_]: Sync](path: String): Resource[F, String] =
    Resource.fromAutoCloseable { Sync[F].blocking(Source.fromResource(path)) }.map(_.mkString(""))

  def bytesFrom[F[_]: Async](path: String): fs2.Stream[F, Byte] =
    Files.forAsync[F].readAll(Path(getClass.getClassLoader.getResource(path).getPath()))
