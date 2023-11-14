package org.fiume.sketch.shared.app.algebras

import org.fiume.sketch.shared.app.algebras.Versions.Version

trait Versions[F[_]]:
  def currentVersion: F[Version]

// TODO Move it to the `shared.app` package
object Versions:
  case class Environment(name: String) extends AnyVal
  case class Build(name: String) extends AnyVal
  case class Commit(name: String) extends AnyVal
  case class Version(env: Environment, build: Build, commit: Commit)
