package org.fiume.sketch.shared.app.typeclasses

trait FromString[E, T]:
  def fromString(s: String): Either[E, T]

object FromStringSyntax:
  extension [E, T](s: String)(using F: FromString[E, T]) def parsed(): Either[E, T] = F.fromString(s)
