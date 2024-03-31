package org.fiume.sketch.shared.app.algebras

import org.fiume.sketch.shared.app.ServiceStatus
import org.fiume.sketch.shared.app.ServiceStatus.{Dependency, DependencyStatus}

object HealthChecker:

  /*
   * Checks the overall health of a resource a services relies on.
   */
  trait DependencyHealthChecker[F[_], T <: Dependency]:
    def check(): F[DependencyStatus[T]]
