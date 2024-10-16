package org.fiume.sketch.shared.common.algebras

import org.fiume.sketch.shared.common.Version

trait Versions[F[_]]:
  def currentVersion: F[Version]
