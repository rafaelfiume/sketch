package org.fiume.sketch.shared.app

import cats.implicits.*
import munit.ScalaCheckSuite
import org.fiume.sketch.shared.app.EntityId.given
import org.fiume.sketch.shared.app.OrderId
import org.fiume.sketch.shared.app.OrderId.given
import org.fiume.sketch.shared.testkit.syntax.EitherSyntax.*
import org.fiume.sketch.shared.typeclasses.FromString
import org.scalacheck.{Arbitrary, Gen, ShrinkLowPriority}
import org.scalacheck.Prop.forAll

import java.util.UUID

class EntityIdSpec extends ScalaCheckSuite with EntityIdSpecContext with ShrinkLowPriority:

  test("AsString and FromString form an isomorphism"):
    forAll { (id: OrderId) =>
      id.asString().parsed().rightOrFail === id &&
      id.entityType === "OrderEntity" &&
      id.entityType === id.asString().parsed().rightOrFail.entityType
    }

  /*
   * See EntityId documentation for more information about the difference between `==` and `===`.
   */
  test("Equality is based on the UUID"):
    forAll { (fst: OrderId, snd: OrderId) =>
      (fst == snd) === (fst.value === snd.value)
    }

trait EntityIdSpecContext:
  given Arbitrary[OrderId] = Arbitrary(orderIds)
  def orderIds: Gen[OrderId] = Gen.uuid.map(OrderId(_))

type OrderId = EntityId[OrderEntity]
object OrderId:
  def apply(uuid: UUID) = EntityId[OrderEntity](uuid)
  given FromString[InvalidUuid, OrderId] = EntityId.FromString.forEntityId(OrderId.apply)
sealed trait OrderEntity extends Entity
