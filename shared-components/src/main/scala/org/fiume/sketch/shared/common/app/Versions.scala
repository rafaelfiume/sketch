package org.fiume.sketch.shared.common.app

trait Versions[F[_]]:
  def currentVersion: F[Version]
