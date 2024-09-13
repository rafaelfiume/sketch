package org.fiume.sketch.authorisation.testkit

import org.fiume.sketch.authorisation.{ContextualRole, GlobalRole, Role}
import org.scalacheck.{Arbitrary, Gen}

object AccessControlGens:

  given Arbitrary[Role] = Arbitrary(roles)
  def roles: Gen[Role] = Gen.oneOf(globalRoles.map(Role.Global(_)), contextualRoles.map(Role.Contextual(_)))

  given Arbitrary[GlobalRole] = Arbitrary(globalRoles)
  def globalRoles: Gen[GlobalRole] = Gen.oneOf(GlobalRole.values.toSeq)

  given Arbitrary[ContextualRole] = Arbitrary(contextualRoles)
  def contextualRoles: Gen[ContextualRole] = Gen.oneOf(ContextualRole.values.toSeq)
