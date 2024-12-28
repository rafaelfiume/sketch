package org.fiume.sketch.shared.common

import cats.{Eq, Show}
import cats.implicits.*
import org.fiume.sketch.shared.common.InvalidUuid.UnparsableUuid
import org.fiume.sketch.shared.common.troubleshooting.InvariantError
import org.fiume.sketch.shared.common.typeclasses.{AsString, FromString}

import java.util.UUID
import scala.util.Try

/*
 * A phantom type is a parameterised type whose parameters do not all appear on the right-hand side of its definition.
 * See https://wiki.haskell.org/Phantom_type
 *
 * About EntityId Equality:
 *
 * val fst = OrderId(uuid)
 * val snd = ItemId(uuid)
 * fst === snd // boom! it won't compile
 * fst == snd // true!!!!
 *
 * Whever possible, it is recommended to favour cats `Eq` over the standard `equals` function,
 * since the former is based both on the UUID value _and_ entityType.
 */
abstract case class EntityId[T <: Entity](val value: UUID):
  def entityType: String
  override def toString(): String = value.toString()

object EntityId:
  inline def apply[T <: Entity](value: UUID): EntityId[T] = ${ Macros.entityIdApplyMacro[T]('value) }

  given [T <: Entity]: AsString[EntityId[T]] with
    extension (id: EntityId[T]) override def asString(): String = id.value.toString

  object FromString:
    def forEntityId[T <: Entity](factory: UUID => EntityId[T]): FromString[InvalidUuid, EntityId[T]] =
      new FromString[InvalidUuid, EntityId[T]]:
        extension (id: String)
          override def parsed(): Either[InvalidUuid, EntityId[T]] =
            Try(UUID.fromString(id)).toEither
              .map(factory)
              .leftMap(_ => UnparsableUuid(id))

  given [T <: Entity]: Eq[EntityId[T]] = Eq.instance { (thiss, other) =>
    (thiss.value === other.value) && (thiss.entityType === other.entityType)
  }

  given Show[EntityId[?]] = Show.fromToString

trait Entity

enum InvalidUuid(val key: String, val detail: String) extends InvariantError:
  case UnparsableUuid(value: String) extends InvalidUuid("invalid.uuid", s"invalid uuid '$value'")

trait WithUuid[T <: EntityId[?]]:
  val uuid: T
