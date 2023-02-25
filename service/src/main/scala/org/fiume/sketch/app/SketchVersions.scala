package org.fiume.sketch.app

import cats.effect.Sync
import cats.implicits.*
import org.fiume.sketch.algebras.{Version, Versions}

import scala.io.Source

object SketchVersions:
  def make[F[_]](using F: Sync[F]): F[Versions[F]] =
    F.delay {
      Source
        .fromResource("sketch.version")
        .getLines()
        .toSeq
        .headOption
        .getOrElse(throw new RuntimeException("Unable to load version"))
    }.map { appVersion =>
      new Versions[F]:
        def currentVersion = Version(appVersion).pure[F]
    }
