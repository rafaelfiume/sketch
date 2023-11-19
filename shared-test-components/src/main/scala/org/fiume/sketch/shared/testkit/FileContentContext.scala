package org.fiume.sketch.shared.testkit

import cats.effect.{Async, Resource, Sync}
import fs2.io.file.{Files, Path}
import fs2.io.file.Files.*
import io.circe.Json
import io.circe.parser.parse
import org.fiume.sketch.shared.testkit.EitherSyntax.*

import scala.io.Source

/*
 * Path is relative to `resource` folders.
 */
trait FileContentContext:
  def jsonFrom[F[_]: Sync](path: String, debug: Boolean = false): Resource[F, Json] =
    stringFrom(path, debug).map(parse(_).rightValue)

  def stringFrom[F[_]: Sync](path: String, debug: Boolean = false): Resource[F, String] =
    Resource
      .fromAutoCloseable { Sync[F].blocking(Source.fromResource(path)) }
      .map(_.mkString(""))
      .evalTap(content => if debug then debugContent(content) else Sync[F].unit)

  def bytesFrom[F[_]: Async](path: String): fs2.Stream[F, Byte] =
    Files.forAsync[F].readAll(Path(getClass.getClassLoader.getResource(path).getPath()))

  private def debugContent[F[_]: Sync](content: String): F[Unit] = Sync[F].delay {
    println(
      s"""|Note that flag `debug` is enabled, so the content of file is printed below:
          |$content
         """.stripMargin
    )
  }
