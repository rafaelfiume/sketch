package org.fiume.sketch.app

import cats.effect.{Resource, Sync}
import cats.implicits.*
import org.fiume.sketch.shared.app.Version
import org.fiume.sketch.shared.app.algebras.Versions

import scala.io.Source

object SketchVersions:
  def make[F[_]](using F: Sync[F]): Resource[F, Versions[F]] =
    Resource
      .fromAutoCloseable {
        F.blocking(Source.fromResource("sketch.version"))
      }
      .map { source =>
        source
          .getLines()
          .toSeq
          .headOption
          .getOrElse(throw new RuntimeException("Unable to load version"))
      }
      .map { version =>
        new Versions[F]:
          def currentVersion = Version(version).pure[F]
      }
