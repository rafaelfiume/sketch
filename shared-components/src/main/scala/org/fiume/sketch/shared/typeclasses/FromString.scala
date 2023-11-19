package org.fiume.sketch.shared.typeclasses

// See https://docs.scala-lang.org/scala3/reference/contextual/type-classes.html#

/*
 * Laws:
 * - Isomorphism: `AsString[T].asString(t: T)` <-> `FromString[T].fromString(t: T).rightValue`
 */
trait FromString[E, T]:
  def fromString(s: String): Either[E, T]

object FromStringSyntax:
  // TODO Rename it
  extension [E, T](s: String)(using F: FromString[E, T]) def parsed(): Either[E, T] = F.fromString(s)
