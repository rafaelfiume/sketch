package org.fiume.sketch.app

import cats.effect.Sync
import cats.implicits.*

import scala.io.Source

opaque type Version = String

object Version:
  def apply(value: String): Version = value

  extension (version: Version) def value: String = version

trait Versions[F[_]]:
  def currentVersion: F[Version]

object Versions:
  def make[F[_]: Sync]: F[Versions[F]] =
    Sync[F]
      .delay {
        Source
          .fromResource("sketch.version")
          .getLines()
          .toSeq
          .headOption
          .getOrElse(throw new RuntimeException("Unable to load version"))
      }
      .map { appVersion =>
        new Versions[F] {
          def currentVersion = Version(appVersion).pure[F]
        }
      }
