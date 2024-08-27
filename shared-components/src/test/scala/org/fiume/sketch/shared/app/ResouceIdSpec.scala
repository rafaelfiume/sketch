package org.fiume.sketch.shared.app

import cats.implicits.*
import munit.ScalaCheckSuite
import org.fiume.sketch.shared.app.ResourceId.given
import org.fiume.sketch.shared.testkit.Syntax.EitherSyntax.*
import org.scalacheck.{Arbitrary, Gen, ShrinkLowPriority}
import org.scalacheck.Prop.forAll

import java.util.UUID

class ResouceIdSpec extends ScalaCheckSuite with ResouceIdSpecContext with ShrinkLowPriority:

  test("AsString and FromString form an isomorphism"):
    forAll { (id: OrderId) =>
      id.asString().parsed().rightValue === id
    }

trait ResouceIdSpecContext:
  type OrderId = ResourceId[Order]
  object OrderId:
    def apply(uuid: UUID) = ResourceId[Order](uuid)

  sealed trait Order extends Resource

  given Arbitrary[OrderId] = Arbitrary(orderIds)
  def orderIds: Gen[OrderId] = Gen.uuid.map(OrderId(_))
