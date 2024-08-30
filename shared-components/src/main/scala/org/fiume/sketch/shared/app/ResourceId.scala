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
abstract case class ResourceId[T <: Resource](val value: UUID):
  def resourceType: String

object ResourceId:
  inline def apply[T <: Resource](value: UUID): ResourceId[T] = ${ Meta.resouceIdApplyMacro[T]('value) }

  given [T <: Resource]: AsString[ResourceId[T]] = new AsString[ResourceId[T]]:
    extension (id: ResourceId[T]) override def asString(): String = id.value.toString

  given [T <: Resource]: FromString[InvalidUuid, ResourceId[T]] = new FromString[InvalidUuid, ResourceId[T]]:
    extension (id: String)
      override def parsed() =
        Try(UUID.fromString(id)).toEither.map(ResourceId[T](_)).leftMap(_ => UnparsableUuid(id))

  given equality[T <: Resource]: Eq[ResourceId[T]] = Eq.fromUniversalEquals[ResourceId[T]]

trait Resource

trait InvalidUuid extends InvariantError
object InvalidUuid:
  case class UnparsableUuid(value: String) extends InvalidUuid with NoStackTrace:
    override def uniqueCode: String = "invalid.uuid"
    override val message: String = s"invalid uuid '$value'"

trait WithUuid[T <: ResourceId[?]]:
  val uuid: T
