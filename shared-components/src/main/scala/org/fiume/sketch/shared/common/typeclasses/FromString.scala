package org.fiume.sketch.shared.common.typeclasses

// See https://docs.scala-lang.org/scala3/reference/contextual/type-classes.html#

/*
 * Laws:
 * - Isomorphism: `T.asString()` <-> `T.parsed().rightOrFail`
 */
trait FromString[E, T]:
  extension (s: String) def parsed(): Either[E, T]
