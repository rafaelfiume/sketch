package org.fiume.sketch.http

import org.fiume.sketch.algebras.HealthCheck.ServiceHealth
import org.fiume.sketch.algebras.Version

object Model:
  case class ServiceStatus(version: Version, health: ServiceHealth)
