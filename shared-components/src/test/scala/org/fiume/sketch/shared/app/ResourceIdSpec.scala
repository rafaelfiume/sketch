package org.fiume.sketch.shared.app

import cats.implicits.*
import munit.ScalaCheckSuite
import org.fiume.sketch.shared.app.ResourceId.given
import org.fiume.sketch.shared.testkit.Syntax.EitherSyntax.*
import org.scalacheck.{Arbitrary, Gen, ShrinkLowPriority}
import org.scalacheck.Prop.forAll

import java.util.UUID

class ResourceIdSpec extends ScalaCheckSuite with ResouceIdSpecContext with ShrinkLowPriority:

  test("AsString and FromString form an isomorphism"):
    forAll { (id: OrderId) =>
      id.asString().parsed().rightValue === id
    }

  test("Equality is based on the UUID value"):
    forAll { (fst: OrderId, snd: OrderId) =>
      (fst == snd) === (fst.value == snd.value)
    }

  test("Resource type is the name of the type parameter"):
    val itemId = ItemId(UUID.randomUUID())
    val orderId = OrderId(UUID.randomUUID())

    assertEquals(itemId.resourceType, "Item")
    assertEquals(orderId.resourceType, "Order")

trait ResouceIdSpecContext:
  type OrderId = ResourceId[Order]
  object OrderId:
    def apply(uuid: UUID) = ResourceId[Order](uuid)
  sealed trait Order extends Resource

  type ItemId = ResourceId[Item]
  object ItemId:
    def apply(uuid: UUID) = ResourceId[Item](uuid)
  sealed trait Item extends Resource

  given Arbitrary[OrderId] = Arbitrary(orderIds)
  def orderIds: Gen[OrderId] = Gen.uuid.map(OrderId(_))
