package org.fiume.sketch.shared.app.algebras

import org.fiume.sketch.shared.app.ServiceHealth

trait HealthCheck[F[_]]:
  def check: F[ServiceHealth]
