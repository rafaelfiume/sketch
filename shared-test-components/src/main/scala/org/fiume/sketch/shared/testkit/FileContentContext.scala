package org.fiume.sketch.shared.testkit

import cats.effect.{Async, Resource, Sync}
import fs2.io.file.{Files, Path}
import fs2.io.file.Files.*

import scala.io.Source

/*
 * Path is relative to `resource` folders.
 */
trait FileContentContext:
  /* reads nicer in tests */
  def jsonFrom[F[_]: Sync](path: String): Resource[F, String] =
    stringsFrom(path)

  def stringsFrom[F[_]: Sync](path: String): Resource[F, String] =
    Resource.fromAutoCloseable { Sync[F].blocking(Source.fromResource(path)) }.map(_.mkString(""))

  def bytesFrom[F[_]: Async](path: String): fs2.Stream[F, Byte] =
    Files.forAsync[F].readAll(Path(getClass.getClassLoader.getResource(path).getPath()))
