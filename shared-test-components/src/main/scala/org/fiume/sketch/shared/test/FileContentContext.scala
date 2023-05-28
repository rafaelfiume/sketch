package org.fiume.sketch.shared.test

import cats.effect.{Async, Resource, Sync}
import fs2.io.file.{Files, Path}
import fs2.io.file.Files.*

import scala.io.Source

/*
 * Path is relative to `resource` folders.
 */
trait FileContentContext:
  /* reads nicer in tests */
  def jsonFrom[F[_]](path: String)(using F: Sync[F]): Resource[F, String] =
    stringsFrom(path)

  def stringsFrom[F[_]](path: String)(using F: Sync[F]): Resource[F, String] =
    Resource.fromAutoCloseable { F.blocking(Source.fromResource(path)) }.map(_.mkString(""))

  def bytesFrom[F[_]](path: String)(using F: Async[F]): fs2.Stream[F, Byte] =
    Files.forAsync[F].readAll(Path(getClass.getClassLoader.getResource(path).getPath()))
