package org.fiume.sketch.shared.app.algebras

import org.fiume.sketch.shared.app.ServiceStatus
import org.fiume.sketch.shared.app.ServiceStatus.{Dependency, DependencyStatus}

trait HealthCheck[F[_]]:
  def check: F[ServiceStatus]

object HealthCheck:

  /*
   * Checks the overall health of a resource a services relies on.
   */
  trait DependencyHealth[F[_], T <: Dependency]:
    def check(): F[DependencyStatus[T]]
