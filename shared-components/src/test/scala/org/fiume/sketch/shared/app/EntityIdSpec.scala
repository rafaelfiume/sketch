package org.fiume.sketch.shared.app

import cats.implicits.*
import munit.ScalaCheckSuite
import org.fiume.sketch.shared.app.EntityId.given
import org.fiume.sketch.shared.testkit.Syntax.EitherSyntax.*
import org.scalacheck.{Arbitrary, Gen, ShrinkLowPriority}
import org.scalacheck.Prop.forAll

import java.util.UUID

class EntityIdSpec extends ScalaCheckSuite with EntityIdSpecContext with ShrinkLowPriority:

  test("AsString and FromString form an isomorphism"):
    forAll { (id: OrderId) =>
      id.asString().parsed().rightValue === id
    }

  test("Equality is based on the UUID value"):
    forAll { (fst: OrderId, snd: OrderId) =>
      (fst == snd) === (fst.value == snd.value)
    }

  test("entity type is the name of the type parameter"):
    val itemId = ItemId(UUID.randomUUID())
    val orderId = OrderId(UUID.randomUUID())

    assertEquals(itemId.entityType, "ItemEntity")
    assertEquals(orderId.entityType, "OrderEntity")

trait EntityIdSpecContext:
  type OrderId = EntityId[OrderEntity]
  object OrderId:
    def apply(uuid: UUID) = EntityId[OrderEntity](uuid)
  sealed trait OrderEntity extends Entity

  type ItemId = EntityId[ItemEntity]
  object ItemId:
    def apply(uuid: UUID) = EntityId[ItemEntity](uuid)
  sealed trait ItemEntity extends Entity

  given Arbitrary[OrderId] = Arbitrary(orderIds)
  def orderIds: Gen[OrderId] = Gen.uuid.map(OrderId(_))
