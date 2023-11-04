package org.fiume.sketch.shared.app

import cats.Eq

import java.util.UUID
import scala.util.Try

trait WithUuid[T <: EntityUuid[?]]:
  val uuid: T

trait Entity

// A phantom type is a parameterised type whose parameters do not all appear on the right-hand side of its definition.
// See https://wiki.haskell.org/Phantom_type
case class EntityUuid[T <: Entity](value: UUID) extends AnyVal

object EntityUuid:
  def fromString[T <: Entity](uuid: String): Either[Throwable, EntityUuid[T]] =
    Try(UUID.fromString(uuid)).toEither.map(EntityUuid[T](_))

  given equality[T <: Entity]: Eq[EntityUuid[T]] = Eq.fromUniversalEquals[EntityUuid[T]]
