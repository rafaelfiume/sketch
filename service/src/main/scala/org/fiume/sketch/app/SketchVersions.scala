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
        val lines = source.getLines().toSeq
        val version = lines.headOption.getOrElse(throw new RuntimeException("No build number specified in 'sketch.version'"))
        val commit = lines.tail.headOption.getOrElse(throw new RuntimeException("No commit hash specified in 'sketch.version'"))
        (version, commit)
      }
      .map { (build, commit) =>
        new Versions[F]:
          def currentVersion = Version(build, commit).pure[F]
      }
