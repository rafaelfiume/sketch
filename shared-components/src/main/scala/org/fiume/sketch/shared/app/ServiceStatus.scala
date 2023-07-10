package org.fiume.sketch.shared.app

import org.fiume.sketch.shared.app.algebras.HealthCheck.ServiceHealth
import org.fiume.sketch.shared.app.algebras.Versions.Version

case class ServiceStatus(version: Version, health: ServiceHealth)
