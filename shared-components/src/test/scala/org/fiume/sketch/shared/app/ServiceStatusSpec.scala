package org.fiume.sketch.shared.app

import cats.implicits.*
import munit.ScalaCheckSuite
import org.fiume.sketch.shared.app.ServiceStatus.{DependencyStatus, Status}
import org.fiume.sketch.shared.app.ServiceStatus.Status.given
import org.fiume.sketch.shared.app.testkit.ServicesStatusGens.*
import org.fiume.sketch.shared.app.testkit.VersionGens.given
import org.scalacheck.{Arbitrary, ShrinkLowPriority}
import org.scalacheck.Prop.forAll

class ServiceStatusSpec extends ScalaCheckSuite with ShrinkLowPriority:

  property("establishes service good health") {
    given Arbitrary[List[DependencyStatus[?]]] = Arbitrary(healthyDependencies)
    forAll { (version: Version, onlyHealthyDependencies: List[DependencyStatus[?]]) =>
      ServiceStatus.make(version, onlyHealthyDependencies).status === Status.Ok
    }
  }

  property("establishes service poor health") {
    given Arbitrary[List[DependencyStatus[?]]] = Arbitrary(unhealthyDependencies)
    forAll { (version: Version, unhealthyDependencies: List[DependencyStatus[?]]) =>
      ServiceStatus.make(version, unhealthyDependencies).status === Status.Degraded
    }
  }
