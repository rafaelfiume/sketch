package org.fiume.sketch.shared.app.algebras

import org.fiume.sketch.shared.app.algebras.Versions.Version

trait Versions[F[_]]:
  def currentVersion: F[Version]

object Versions:
  case class Environment(name: String) extends AnyVal
  case class Version(env: Environment, build: String, commit: String)
