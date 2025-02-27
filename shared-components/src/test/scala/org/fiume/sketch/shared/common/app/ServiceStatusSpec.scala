package org.fiume.sketch.shared.common.app

import cats.implicits.*
import munit.ScalaCheckSuite
import org.fiume.sketch.shared.common.app.ServiceStatus.{DependencyStatus, Status}
import org.fiume.sketch.shared.common.app.ServiceStatus.Status.given
import org.fiume.sketch.shared.common.testkit.ServicesStatusGens.*
import org.fiume.sketch.shared.common.testkit.VersionGens.given
import org.scalacheck.{Arbitrary, ShrinkLowPriority}
import org.scalacheck.Prop.forAll

class ServiceStatusSpec extends ScalaCheckSuite with ShrinkLowPriority:

  property("service status is ok when all dependencies are healthy") {
    given Arbitrary[List[DependencyStatus[?]]] = Arbitrary(healthyDependencies)
    forAll { (version: Version, onlyHealthyDependencies: List[DependencyStatus[?]]) =>
      org.fiume.sketch.shared.common.app.ServiceStatus.make(version, onlyHealthyDependencies).status === Status.Ok
    }
  }

  property("service status is degraded when at least one dependency in unhealthy") {
    given Arbitrary[List[DependencyStatus[?]]] = Arbitrary(unhealthyDependencies)
    forAll { (version: Version, unhealthyDependencies: List[DependencyStatus[?]]) =>
      org.fiume.sketch.shared.common.app.ServiceStatus.make(version, unhealthyDependencies).status === Status.Degraded
    }
  }
