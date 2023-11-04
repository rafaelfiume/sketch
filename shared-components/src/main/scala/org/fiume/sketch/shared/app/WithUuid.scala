package org.fiume.sketch.shared.app

import cats.Eq

import java.util.UUID

trait WithUuid[T <: EntityUuid[?]]:
  val uuid: T

// A phantom type is a parameterised type whose parameters do not all appear on the right-hand side of its definition.
// See https://wiki.haskell.org/Phantom_type
case class EntityUuid[T](value: UUID) extends AnyVal

object EntityUuid:
  given equality[T]: Eq[EntityUuid[T]] = Eq.fromUniversalEquals[EntityUuid[T]]
