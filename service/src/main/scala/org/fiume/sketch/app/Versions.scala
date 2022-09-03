package org.fiume.sketch.app

import cats.effect.Sync
import cats.implicits.*

import scala.io.Source

case class Version(value: String) extends AnyVal

trait Versions[F[_]]:
  def currentVersion: F[Version]

object Versions:
  def make[F[_]](using F: Sync[F]): F[Versions[F]] =
    F.delay {
      Source
        .fromResource("sketch.version")
        .getLines()
        .toSeq
        .headOption
        .getOrElse(throw new RuntimeException("Unable to load version"))
    }.map { appVersion =>
      new Versions[F] {
        def currentVersion = Version(appVersion).pure[F]
      }
    }
