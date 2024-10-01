package org.fiume.sketch.shared.app

import cats.Eq
import cats.implicits.*
import org.fiume.sketch.shared.app.InvalidUuid.UnparsableUuid
import org.fiume.sketch.shared.app.troubleshooting.InvariantError
import org.fiume.sketch.shared.typeclasses.{AsString, FromString}

import java.util.UUID
import scala.util.Try
import scala.util.control.NoStackTrace

// A phantom type is a parameterised type whose parameters do not all appear on the right-hand side of its definition.
// See https://wiki.haskell.org/Phantom_type
abstract case class EntityId[T <: Entity](val value: UUID):
  def entityType: String

object EntityId:
  inline def apply[T <: Entity](value: UUID): EntityId[T] = ${ Macros.entityIdApplyMacro[T]('value) }

  given [T <: Entity]: AsString[EntityId[T]] = new AsString[EntityId[T]]:
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

trait Entity

trait InvalidUuid extends InvariantError
object InvalidUuid:
  case class UnparsableUuid(value: String) extends InvalidUuid with NoStackTrace:
    override def uniqueCode: String = "invalid.uuid"
    override val message: String = s"invalid uuid '$value'"

trait WithUuid[T <: EntityId[?]]:
  val uuid: T
