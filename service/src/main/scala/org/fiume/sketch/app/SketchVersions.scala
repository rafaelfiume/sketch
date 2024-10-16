package org.fiume.sketch.app

import cats.effect.{Resource, Sync}
import cats.implicits.*
import org.fiume.sketch.shared.common.Version
import org.fiume.sketch.shared.common.Version.{Build, Commit, Environment}
import org.fiume.sketch.shared.common.algebras.Versions

import scala.io.Source

object SketchVersions:
  /* See `(Compile / resourceManaged).value / "${value}.version"` in build.sbt */
  case class VersionFile(name: String) extends AnyVal

  def make[F[_]: Sync](env: Environment, versionFile: VersionFile): Resource[F, Versions[F]] =
    Resource
      .fromAutoCloseable {
        Sync[F].blocking(Source.fromResource(versionFile.name))
      }
      .map { source =>
        val lines = source.getLines().toSeq
        val build = lines.headOption
          .map(Build(_))
          .getOrElse(throw new RuntimeException(s"No build number specified in '${versionFile.name}'"))
        val commit = lines.tail.headOption
          .map(Commit(_))
          .getOrElse(throw new RuntimeException(s"No commit hash specified in '${versionFile.name}'"))
        (build, commit)
      }
      .map { (build, commit) =>
        new Versions[F]:
          def currentVersion = Version(env, build, commit).pure[F]
      }
