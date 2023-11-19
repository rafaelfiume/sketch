package org.fiume.sketch.shared.app

import cats.implicits.*
import munit.ScalaCheckSuite
import org.fiume.sketch.shared.app.EntityId.given
import org.fiume.sketch.shared.testkit.EitherSyntax.*
import org.scalacheck.{Arbitrary, Gen, ShrinkLowPriority}
import org.scalacheck.Prop.forAll

import java.util.UUID

class EntityIdSpec extends ScalaCheckSuite with EntityIdSpecContext with ShrinkLowPriority:

  test("AsString <-> FromString (isomorphism)"):
    forAll { (id: OrderId) =>
      id.asString().parsed().rightValue === id
    }

trait EntityIdSpecContext:
  type OrderId = EntityId[Order]
  object OrderId:
    def apply(uuid: UUID) = EntityId[Order](uuid)

  sealed trait Order extends Entity

  given Arbitrary[OrderId] = Arbitrary(orderIds)
  def orderIds: Gen[OrderId] = Gen.uuid.map(OrderId(_))
