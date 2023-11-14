package org.fiume.sketch.shared.app.testkit

import org.fiume.sketch.shared.app.ServiceStatus
import org.fiume.sketch.shared.app.ServiceStatus.{Dependency, DependencyStatus, Status}
import org.fiume.sketch.shared.app.ServiceStatus.Dependency.*
import org.scalacheck.Gen

import scala.util.Random

object ServicesStatusGens:
  def bunchOfStatus: Gen[Status] = Gen.oneOf(Status.Ok, Status.Degraded)

  def healthyDependencies: Gen[List[DependencyStatus[?]]] = Gen
    .nonEmptyListOf(dependencies.map(DependencyStatus(_, Status.Ok)))
    .map(_.distinct) :| "healthyDependencies"

  def unhealthyDependencies: Gen[List[DependencyStatus[?]]] =
    for
      allDependencies <- healthyDependencies
      someAboutToGoDown <- Gen.atLeastOne(allDependencies).map(_.toList)
      degraded = someAboutToGoDown.map(_.copy(status = Status.Degraded))
    yield
      val depds = degraded ++ allDependencies.diff(someAboutToGoDown)
      // Random is not ideal because it is non-deterministic:
      // Running a property-based test with the same seed might result in different inputs
      Random.shuffle(depds)

  def dependenciesStatus: Gen[DependencyStatus[Dependency]] =
    for
      dependency <- dependencies
      status <- bunchOfStatus
    yield DependencyStatus(dependency, status)

  def dependencies: Gen[Dependency] = Gen.oneOf(database, profile)
