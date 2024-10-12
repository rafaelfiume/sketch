package org.fiume.sketch.shared.authorisation

import cats.implicits.*
import munit.ScalaCheckSuite
import org.fiume.sketch.shared.authorisation.Role.given
import org.fiume.sketch.shared.authorisation.testkit.AccessControlGens.given
import org.fiume.sketch.shared.testkit.syntax.EitherSyntax.*
import org.scalacheck.Prop.forAll
import org.scalacheck.ShrinkLowPriority

class RoleSpec extends ScalaCheckSuite with ShrinkLowPriority:

  test("AsString and FromString form an isomorphism"):
    forAll { (role: Role) =>
      role.asString().parsed().rightOrFail === role
    }
