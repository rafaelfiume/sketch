package org.fiume.sketch.shared.app

import cats.Eq
import cats.implicits.*
import org.fiume.sketch.shared.app.InvalidId.UnparsableUuid
import org.fiume.sketch.shared.app.troubleshooting.InvariantError
import org.fiume.sketch.shared.typeclasses.{FromString, SemanticString}

import java.util.UUID
import scala.util.Try
import scala.util.control.NoStackTrace

// A phantom type is a parameterised type whose parameters do not all appear on the right-hand side of its definition.
// See https://wiki.haskell.org/Phantom_type
case class EntityId[T <: Entity](value: UUID) extends AnyVal

object EntityId:
  given [T <: Entity]: SemanticString[EntityId[T]] = new SemanticString[EntityId[T]]:
    override def asString(id: EntityId[T]): String = id.value.toString

  given [T <: Entity]: FromString[InvalidId, EntityId[T]] = new FromString[InvalidId, EntityId[T]]:
    override def fromString(id: String) =
      Try(UUID.fromString(id)).toEither.map(EntityId[T](_)).leftMap(_ => UnparsableUuid(id))

  given equality[T <: Entity]: Eq[EntityId[T]] = Eq.fromUniversalEquals[EntityId[T]]

trait Entity

trait InvalidId extends InvariantError
object InvalidId:
  case class UnparsableUuid(value: String) extends InvalidId with NoStackTrace:
    override def uniqueCode: String = "invalid.uuid"
    override val message: String = s"invalid uuid '$value'"

trait WithUuid[T <: EntityId[?]]:
  val uuid: T
