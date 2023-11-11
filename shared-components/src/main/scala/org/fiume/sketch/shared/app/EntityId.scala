package org.fiume.sketch.shared.app

import cats.Eq
import cats.implicits.*
import org.fiume.sketch.shared.app.InvalidId.UnparsableUuid
import org.fiume.sketch.shared.app.troubleshooting.InvariantError

import java.util.UUID
import scala.util.Try
import scala.util.control.NoStackTrace

// A phantom type is a parameterised type whose parameters do not all appear on the right-hand side of its definition.
// See https://wiki.haskell.org/Phantom_type
case class EntityId[T <: Entity](value: UUID) extends AnyVal:
  override def toString: String = value.toString

object EntityId:
  def fromString[T <: Entity](idKey: String)(uuid: String): Either[InvalidId, EntityId[T]] = // Yolo
    Try(UUID.fromString(uuid)).toEither.map(EntityId[T](_)).leftMap(_ => UnparsableUuid(idKey, uuid))

  given equality[T <: Entity]: Eq[EntityId[T]] = Eq.fromUniversalEquals[EntityId[T]]

trait Entity

trait InvalidId extends InvariantError
object InvalidId:
  case class UnparsableUuid(idKey: String, value: String) extends InvalidId with NoStackTrace:
    override def uniqueCode: String = s"invalid.${idKey}"
    override val message: String = s"invalid uuid '$value'"

trait WithUuid[T <: EntityId[?]]:
  val uuid: T
