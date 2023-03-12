package org.fiume.sketch.shared.app

import org.fiume.sketch.shared.app.Version
import org.fiume.sketch.shared.app.algebras.HealthCheck.ServiceHealth

case class ServiceStatus(version: Version, health: ServiceHealth)
