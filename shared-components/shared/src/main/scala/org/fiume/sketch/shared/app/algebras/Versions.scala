package org.fiume.sketch.shared.app.algebras

import org.fiume.sketch.shared.app.Version

trait Versions[F[_]]:
  def currentVersion: F[Version]
