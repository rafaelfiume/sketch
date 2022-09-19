package org.fiume.sketch.http

import org.fiume.sketch.app.Version

object Model:
  case class AppStatus(healthy: Boolean, version: Version)
