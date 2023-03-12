package org.fiume.sketch.app

import org.fiume.sketch.algebras.HealthCheck.ServiceHealth
import org.fiume.sketch.algebras.Version

case class ServiceStatus(version: Version, health: ServiceHealth)
