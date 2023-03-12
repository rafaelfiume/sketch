package org.fiume.sketch.shared.app

import org.fiume.sketch.shared.app.{ServiceHealth, Version}

case class ServiceStatus(version: Version, health: ServiceHealth)
