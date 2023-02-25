package org.fiume.sketch.algebras

case class Version(value: String) extends AnyVal

trait Versions[F[_]]:
  def currentVersion: F[Version]
